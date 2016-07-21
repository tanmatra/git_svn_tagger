package com.github.tanmarta.git_svn_tagger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class Main
{
    private static final String PREFIX = "refs/remotes/svn/tags/";

    public static void main(String[] args) throws IOException, GitAPIException {
        if (args.length < 1) {
            return;
        }
        final File workTree = new File(args[0]);
        if (!workTree.exists() && !workTree.isDirectory()) {
            return;
        }
        final FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .setWorkTree(workTree)
                .readEnvironment();
        try (Repository repository = builder.build()) {
            final Main main = new Main(repository);
            main.run();
        }
    }

    private final Repository repository;

    private final Git git;

    private final List<String> createdTags = new ArrayList<>();

    private Main(Repository repository) {
        this.repository = repository;
        git = new Git(repository);
    }

    private void run() throws GitAPIException, IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            final Map<String, Ref> allTags = repository.getTags();

            final ListBranchCommand listBranchCommand = git.branchList();
            listBranchCommand.setListMode(ListBranchCommand.ListMode.REMOTE);
            final List<Ref> refs = listBranchCommand.call();

            for (Ref ref : refs) {
                final String refName = ref.getName();
                if (!refName.startsWith(PREFIX)) {
                    continue;
                }
                final ObjectId refObjectId = ref.getObjectId();
                final RevCommit commit = revWalk.parseCommit(refObjectId);
                final String message = commit.getShortMessage();
                final String tagName = refName.substring(PREFIX.length());
                System.out.println("Branch:         " + refName);
                System.out.println("Branch id:      " + refObjectId.getName());
                System.out.println("Branch message: " + message);
                System.out.println("Tag name:       " + tagName);

                if (!allTags.containsKey(tagName)) {
                    final int parentCount = commit.getParentCount();
                    final RevCommit parent = commit.getParent(parentCount - 1);
                    revWalk.parseHeaders(parent);
                    System.out.println("Parent id:      " + parent.getName());
                    System.out.println("Parent message: " + parent.getShortMessage());

                    createTag(tagName, parent);
                }

                System.out.println("---");
            }
        }
        if (!createdTags.isEmpty()) {
            System.out.println("Created tags:   " + String.join(" ", createdTags));
        } else {
            System.out.println("No tags created.");
        }
    }

    private void createTag(String tagName, RevCommit commit) {
        final TagCommand tagCommand = git.tag();
        tagCommand.setAnnotated(false);
        tagCommand.setName(tagName);
        tagCommand.setObjectId(commit);
        try {
            final Ref tagRef = tagCommand.call();
            createdTags.add(tagName);
            System.out.println("Tag created:    " + tagRef);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
