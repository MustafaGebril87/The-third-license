package com.thethirdlicense.Util;

import java.io.IOException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;

public class Utils {
	public static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
    try (RevWalk walk = new RevWalk(repository)) {
        ObjectId commitId = ObjectId.fromString(objectId);
        RevCommit commit = walk.parseCommit(commitId);
        RevTree tree = commit.getTree();

        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, tree);
        }
        walk.dispose();
        return treeParser;
    } catch (MissingObjectException e) {
        System.out.println(" Commit not found: " + objectId + " — falling back to EmptyTreeIterator");
        return new EmptyTreeIterator();
    }
}

}
