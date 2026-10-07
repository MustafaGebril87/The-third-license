package com.thethirdlicense.scenarios;

import com.thethirdlicense.models.*;
import com.thethirdlicense.repositories.*;
import com.thethirdlicense.security.JWTUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario: real Git flow (JGit on a temp directory) with the security boundaries enforced.
 *
 * Cast of characters:
 *   - Alice (owner): opens a company through the API → real bare repo is created
 *   - Bob (contributor): gets approved, clones, pushes a feature branch
 *   - Carol (stranger): has no access to the repository
 *
 * Checks: unsafe company names rejected, contributors can't push to main,
 * path traversal can't read or write outside the clone, strangers can't read code,
 * and the owner's merge credits only the merged change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "git.repos.base-path=${java.io.tmpdir}/ttl-gitflow-scenario")
class GitFlowScenarioTest {

    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"), "ttl-gitflow-scenario");

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RepositoryRepository repositoryRepository;
    @Autowired private AccessRequestRepository accessRequestRepository;
    @Autowired private ContributionRepository contributionRepository;
    @Autowired private ShareRepository shareRepository;
    @Autowired private JWTUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    private String suffix;
    private User alice, bob, carol;
    private String aliceJwt, bobJwt, carolJwt;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = userRepository.save(new User("alice_" + suffix, "alice_" + suffix + "@test.com", passwordEncoder.encode("strongpass1"), new HashSet<>()));
        bob = userRepository.save(new User("bob_" + suffix, "bob_" + suffix + "@test.com", passwordEncoder.encode("strongpass1"), new HashSet<>()));
        carol = userRepository.save(new User("carol_" + suffix, "carol_" + suffix + "@test.com", passwordEncoder.encode("strongpass1"), new HashSet<>()));
        aliceJwt = jwtUtil.generateToken(alice);
        bobJwt = jwtUtil.generateToken(bob);
        carolJwt = jwtUtil.generateToken(carol);
    }

    @AfterEach
    void tearDown() {
        companyRepository.findByOwner(alice).forEach(company -> {
            repositoryRepository.findByCompanyId(company.getId()).forEach(repo -> {
                accessRequestRepository.findAll().stream()
                        .filter(ar -> ar.getRepository().getId().equals(repo.getId()))
                        .forEach(accessRequestRepository::delete);
                contributionRepository.findByRepositoryId(repo.getId()).forEach(contributionRepository::delete);
            });
            shareRepository.findByCompanyId(company.getId()).forEach(shareRepository::delete);
            companyRepository.deleteById(company.getId());
        });
        userRepository.deleteById(carol.getId());
        userRepository.deleteById(bob.getId());
        userRepository.deleteById(alice.getId());
    }

    @AfterAll
    static void cleanFiles() throws IOException {
        if (Files.exists(BASE)) {
            try (var walk = Files.walk(BASE)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(f -> { f.setWritable(true); f.delete(); });
            }
        }
    }

    private HttpHeaders bearer(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt);
        return h;
    }

    private HttpHeaders json(String jwt) {
        HttpHeaders h = bearer(jwt);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> push(String jwt, UUID repoId, String branch, String filename, String content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return filename; }
        });
        HttpHeaders h = bearer(jwt);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/contributions/push-files?repositoryId=" + repoId + "&branch=" + branch,
                HttpMethod.POST, new HttpEntity<>(form, h), String.class);
    }

    /** Alice opens a company via the API and Bob is approved and clones it. Returns the repository. */
    private Repository_ companyWithApprovedBob() {
        String name = "GitFlow" + suffix;
        ResponseEntity<String> open = restTemplate.exchange("/api/companies/open", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name), json(aliceJwt)), String.class);
        assertThat(open.getStatusCode()).isEqualTo(HttpStatus.OK);

        Company company = companyRepository.findByOwner(alice).get(0);
        Repository_ repo = repositoryRepository.findByCompanyId(company.getId()).get(0);

        // The founder starts with 100%
        assertThat(company.getTotalUnits()).isEqualTo(1_000_000);
        List<Share> founder = shareRepository.findByCompanyId(company.getId());
        assertThat(founder).hasSize(1);
        assertThat(founder.get(0).getOwner().getId()).isEqualTo(alice.getId());
        assertThat(founder.get(0).getPercentage()).isEqualTo(100.0);

        restTemplate.exchange("/api/contributions/" + repo.getId() + "/request-access", HttpMethod.POST,
                new HttpEntity<>(bearer(bobJwt)), String.class);
        UUID requestId = accessRequestRepository.findPendingByCompanyOwner(alice).get(0).getId();
        ResponseEntity<String> approve = restTemplate.exchange("/api/contributions/requests/" + requestId + "/approve",
                HttpMethod.POST, new HttpEntity<>(bearer(aliceJwt)), String.class);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> clone = restTemplate.exchange("/api/companies/" + repo.getId() + "/clone",
                HttpMethod.POST, new HttpEntity<>(bearer(bobJwt)), String.class);
        assertThat(clone.getStatusCode()).isEqualTo(HttpStatus.OK);
        return repo;
    }

    @Test
    void unsafeCompanyName_isRejected_andNothingWrittenOutsideBase() {
        ResponseEntity<String> resp = restTemplate.exchange("/api/companies/open", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "../escape" + suffix), json(aliceJwt)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(companyRepository.findByOwner(alice)).isEmpty();
        assertThat(BASE.getParent().resolve("escape" + suffix + ".git")).doesNotExist();
    }

    @Test
    void openCompany_cannotHijackExistingCompanyById() {
        companyWithApprovedBob();
        Company aliceCompany = companyRepository.findByOwner(alice).get(0);

        ResponseEntity<String> resp = restTemplate.exchange("/api/companies/open", HttpMethod.POST,
                new HttpEntity<>(Map.of("id", aliceCompany.getId().toString(), "name", "Hijack" + suffix), json(carolJwt)),
                String.class);

        // Carol gets her own new company at most — Alice's company keeps its owner and name
        Company after = companyRepository.findById(aliceCompany.getId()).orElseThrow();
        assertThat(after.getOwner().getId()).isEqualTo(alice.getId());
        assertThat(after.getName()).isEqualTo(aliceCompany.getName());
        if (resp.getStatusCode() == HttpStatus.OK) {
            companyRepository.findByOwner(carol).forEach(c -> {
                repositoryRepository.findByCompanyId(c.getId()).forEach(repositoryRepository::delete);
                companyRepository.deleteById(c.getId());
            });
        }
    }

    @Test
    void contributor_cannotPushToMain_butCanPushFeatureBranch() {
        Repository_ repo = companyWithApprovedBob();

        ResponseEntity<String> toMain = push(bobJwt, repo.getId(), "main", "hello.txt", "hi");
        assertThat(toMain.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> toFeature = push(bobJwt, repo.getId(), "feature-hello", "hello.txt", "hi");
        assertThat(toFeature.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contributionRepository.findByRepositoryIdAndBranchAndStatus(repo.getId(), "feature-hello", ContributionStatus.PENDING))
                .hasSize(1);
    }

    @Test
    void pushWithTraversalFilename_isRejected_andFileNotWritten() {
        Repository_ repo = companyWithApprovedBob();

        ResponseEntity<String> resp = push(bobJwt, repo.getId(), "feature-x", "../../pwned-" + suffix + ".txt", "owned");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(BASE.resolve("pwned-" + suffix + ".txt")).doesNotExist();
        assertThat(BASE.getParent().resolve("pwned-" + suffix + ".txt")).doesNotExist();
    }

    @Test
    void pullWithTraversalPath_cannotReadServerFiles() throws IOException {
        Repository_ repo = companyWithApprovedBob();
        Path secret = BASE.resolve("secret-" + suffix + ".txt");
        Files.writeString(secret, "TOP-SECRET");

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/contributions/pull-file?repositoryId=" + repo.getId() + "&branch=main&filePath=../../secret-" + suffix + ".txt",
                HttpMethod.GET, new HttpEntity<>(bearer(bobJwt)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).doesNotContain("TOP-SECRET");
    }

    @Test
    void stranger_cannotReadDiffOrPull() {
        Repository_ repo = companyWithApprovedBob();

        ResponseEntity<String> diff = restTemplate.exchange(
                "/api/contributions/merge/diff?repositoryId=" + repo.getId() + "&branch=main&filePath=README.md",
                HttpMethod.GET, new HttpEntity<>(bearer(carolJwt)), String.class);
        assertThat(diff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> pull = restTemplate.exchange(
                "/api/contributions/pull-file?repositoryId=" + repo.getId() + "&branch=main&filePath=README.md",
                HttpMethod.GET, new HttpEntity<>(bearer(carolJwt)), String.class);
        assertThat(pull.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerMerge_withTraversalPath_isRejected() {
        Repository_ repo = companyWithApprovedBob();

        Map<String, Object> body = Map.of(
                "repositoryId", repo.getId().toString(),
                "branch", "feature-x",
                "mergeType", "MERGE_REQUEST",
                "files", List.of(Map.of("filePath", "../../../owned-" + suffix + ".txt", "mergedContent", "x")));
        ResponseEntity<String> resp = restTemplate.exchange("/api/contributions/merge-branch", HttpMethod.POST,
                new HttpEntity<>(body, json(aliceJwt)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(BASE.resolve("owned-" + suffix + ".txt")).doesNotExist();
    }

    @Test
    void ownerMerge_approvesContribution_creditingOnlyTheMergedChange() {
        Repository_ repo = companyWithApprovedBob();
        assertThat(push(bobJwt, repo.getId(), "feature-hello", "hello.txt", "line1\nline2\n").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Bob's diff is visible to him and to the owner
        ResponseEntity<String> diff = restTemplate.exchange(
                "/api/contributions/merge/diff?repositoryId=" + repo.getId() + "&branch=feature-hello&filePath=hello.txt",
                HttpMethod.GET, new HttpEntity<>(bearer(aliceJwt)), String.class);
        assertThat(diff.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(diff.getBody()).contains("line1");

        Map<String, Object> body = Map.of(
                "repositoryId", repo.getId().toString(),
                "branch", "feature-hello",
                "mergeType", "MERGE_REQUEST",
                "files", List.of(Map.of("filePath", "hello.txt", "mergedContent", "line1\nline2\n")));
        ResponseEntity<String> merge = restTemplate.exchange("/api/contributions/merge-branch", HttpMethod.POST,
                new HttpEntity<>(body, json(aliceJwt)), String.class);
        assertThat(merge.getStatusCode()).isEqualTo(HttpStatus.OK);

        Contribution c = contributionRepository.findByRepositoryId(repo.getId()).get(0);
        assertThat(c.isApproved()).isTrue();
        assertThat(c.getOriginalCommitHash()).isNotNull();
        // 2 new lines — not the whole repository (README included) as before
        assertThat(c.getModifiedCodeSize()).isEqualTo(2);
        assertThat(c.getStatus()).isEqualTo(ContributionStatus.ACCEPTED);

        // Equity: Alice founded with 1,000,000 units; Bob earned 2 lines × 100 = 200 units
        Company company = companyRepository.findById(repo.getCompany().getId()).orElseThrow();
        assertThat(company.getTotalUnits()).isEqualTo(1_000_200);
        List<Share> shares = shareRepository.findByCompanyId(company.getId());
        Share aliceShare = shares.stream().filter(s -> s.getOwner().getId().equals(alice.getId())).findFirst().orElseThrow();
        Share bobShare = shares.stream().filter(s -> s.getOwner().getId().equals(bob.getId())).findFirst().orElseThrow();
        assertThat(aliceShare.getUnits()).isEqualTo(1_000_000);
        assertThat(bobShare.getUnits()).isEqualTo(200);
        assertThat(aliceShare.getPercentage() + bobShare.getPercentage()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1e-9));

        // Merging the same branch again must not pay Bob twice
        restTemplate.exchange("/api/contributions/merge-branch", HttpMethod.POST,
                new HttpEntity<>(body, json(aliceJwt)), String.class);
        assertThat(companyRepository.findById(company.getId()).orElseThrow().getTotalUnits()).isEqualTo(1_000_200);
    }
}
