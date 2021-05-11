package dev.snowdrop.release.services;

import dev.snowdrop.release.model.cpaas.ReleaseMustache;
import dev.snowdrop.release.model.cpaas.product.*;
import dev.snowdrop.release.model.cpaas.release.CPaaSAdvisory;
import dev.snowdrop.release.model.cpaas.release.CPaaSReleaseFile;
import dev.snowdrop.release.model.cpaas.release.CPaaSTool;
import org.eclipse.jgit.lib.Repository;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class CPaaSConfigUpdateService {
    public static final String CPAAS_REPO_NAME = "cpaas-products/springboot";
    private static final Logger LOG = Logger.getLogger(CPaaSConfigUpdateService.class);
    private static final String RELEASE_TEMPLATE = "cpaas_release.mustache";

    @Inject
    CPaaSReleaseFactory factory;

    @Inject
    GitService git;

    public GitService.GitConfig buildGitConfig(final String release, final String gitlabUser, final String gitlabPw, final Optional<String> previousRelease, final Optional<String> gitRef) {
        return GitService.GitConfig.gitlabConfig(release, gitlabUser, gitlabPw, (gitRef.isEmpty() ? CPAAS_REPO_NAME : gitRef.get()), previousRelease, Optional.of(release));
    }

    /**
     * <p>STEP to be executed and the start of the release. </p>
     * <p>Create a new branch for the release in the CPaaS folder and removes the advisory that might be used in a previous version.</p>
     * <p>WARNING: It should be executed only at the start of a new release as it removes the advisory file, with the start-release command.</p>
     *
     * @param release
     * @param previousRelease
     * @return
     * @throws IOException
     */
    public void newRelease(GitService.GitConfig cpaasGitlabConfig, final String release, final String previousRelease, boolean isErDr, boolean isSecurityAdvisory) throws IOException {
        git.commitAndPush("chore: update configuration for release' key [release-manager]", cpaasGitlabConfig, repo -> {
            Stream<File> fileStream = updateCPaaSFiles(release, repo, previousRelease, isErDr,isSecurityAdvisory);
            final String repoPath = repo.getAbsolutePath();
            final Path advisoryPath = Paths.get(String.format(repoPath + "/advisory_map.yml"));
            final File advisoryFile = advisoryPath.toFile();
            if (advisoryFile.exists()) {
                advisoryFile.delete();
                Stream.Builder<File> builder = Stream.builder();
                fileStream.forEach(file -> {
                    builder.add(file);
                });
                builder.add(advisoryFile);
                fileStream = builder.build();
            }
            return fileStream;
        });
    }

    public Stream<File> updateCPaaSFiles(String release, File repo, String previousRelease, boolean isErCr, boolean isSecurityAdvisory) {
        String[] releaseArray = release.split("\\.");
        if (releaseArray.length != 3) {
            throw new IllegalArgumentException("Invalid release: " + release
                + ". Must follow major.minor.fix format (e.g. 2.4.3).");
        }
        final String repoPath = repo.getAbsolutePath();
        List<File> fileList = new LinkedList();
//        final Path advisoryPath = Paths.get(String.format(repoPath + "/advisory_map.yml"));
//        final File advisoryFile = advisoryPath.toFile();
        final Path productPath = Paths.get(String.format(repoPath + "/product.yml"));
        final File productFile = productPath.toFile();
        final Path releasePath = Paths.get(String.format(repoPath + "/release.yml"));
        final File releaseFile = releasePath.toFile();
//        if (advisoryFile.exists()) {
//            advisoryFile.delete();
//            fileList.add(advisoryFile);
//        }
        if (productFile.exists()) {
            try {
                InputStream productIS = new FileInputStream(productFile);
                CPaaSProductFile cpaasProductFile = factory.createCPaaSProductFromStream(productIS);
                CPaaSProduct cpaasProduct = cpaasProductFile.getProduct();
                cpaasProduct.getRelease().setVersion(release);
                CPaaSProject projects = cpaasProduct.getProjects().get(0);
                if ("spring-boot-parent".equalsIgnoreCase(projects.getName())) {
                    CPaaSComponent sbComponent = projects.getComponents().get(0);
                    if ("spring-boot".equalsIgnoreCase(sbComponent.getName())) {
                        CPaaSBuild sbBuild = sbComponent.getBuilds().get(0);
                        if ("spring-boot".equalsIgnoreCase(sbBuild.getName())) {
                            sbBuild.getPigSource().setRoot(String.format("spring-boot/%s.%s", releaseArray[0], releaseArray[1]));
                        }
                    }
                }
                factory.saveTo(cpaasProductFile, productFile);
                fileList.add(productFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            ReleaseMustache releaseMustache = new ReleaseMustache(isErCr,isSecurityAdvisory,null,release,previousRelease);
            StringWriter writer = new StringWriter();
            Utility.mf.compile(RELEASE_TEMPLATE).execute(writer, releaseMustache).flush();
            CPaaSReleaseFile cpaasReleaseFile = factory.createCPaaSReleaseFromStream(new ByteArrayInputStream(writer.toString().getBytes()));
            factory.saveTo(cpaasReleaseFile, releaseFile);
            fileList.add(releaseFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileList.stream();
    }


}
