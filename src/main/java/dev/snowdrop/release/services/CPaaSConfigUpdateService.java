package dev.snowdrop.release.services;

import dev.snowdrop.release.model.CVE;
import dev.snowdrop.release.model.Release;
import dev.snowdrop.release.model.cpaas.product.*;
import dev.snowdrop.release.model.cpaas.release.CPaaSReleaseFile;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class CPaaSConfigUpdateService {
    public static final String CPAAS_REPO_NAME = "snowdrop/springboot";
    private static final Logger LOG = Logger.getLogger(CPaaSConfigUpdateService.class);

    @Inject
    CPaaSReleaseFactory factory;

    @Inject
    GitService git;

    @Inject
    CVEService cveService;

    public GitService.GitConfig buildGitConfig(final Release releaseObject, final String gitlabUser, final String gitlabPw, final Optional<String> gitRef) {
        return GitService.GitConfig.gitlabConfig(releaseObject.getVersion(), gitlabUser, gitlabPw, (gitRef.isEmpty() ? CPAAS_REPO_NAME : gitRef.get()), Optional.of("master"), Optional.of(releaseObject.getVersion()));
    }

    /**
     * <p>STEP to be executed and the start of the release. </p>
     * <p>Create a new branch for the release in the CPaaS folder and removes the advisory that might be used in a previous version.</p>
     * <p>WARNING: It should be executed only at the start of a new release as it removes the advisory file, with the start-release command.</p>
     *
     * @param cpaasGitlabConfig
     * @param release
     * @param createAdvisory
     * @throws IOException
     */
    public void newRelease(GitService.GitConfig cpaasGitlabConfig, final Release release, boolean createAdvisory) throws IOException {
            git.commitAndPush("chore: update configuration for release' key [release-manager]", cpaasGitlabConfig, repo -> {
                Stream<File> fileStream = null;
                fileStream = updateCPaaSFiles(release, repo, createAdvisory);
                final String repoPath = repo.getAbsolutePath();
                final Path advisoryPath = Paths.get(String.format(repoPath + "/" + release.getCpaas().getAdvisoryFile()));
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
                if (!release.isTestMode()) {
                    return fileStream;
                } else {
                    return Stream.empty();
                }
            });
    }

    public Stream<File> updateCPaaSFiles(final Release release, File repo, boolean createAdvisory) {
        String[] releaseArray = release.getVersion().split("\\.");
        if (releaseArray.length != 3) {
            throw new IllegalArgumentException("Invalid release: " + release
                + ". Must follow major.minor.fix format (e.g. 2.4.3).");
        }
        final String repoPath = repo.getAbsolutePath();
        List<File> fileList = new ArrayList(3);
        final Path productPath = Paths.get(String.format(repoPath + "/" + release.getCpaas().getProductFile()));
        final File productFile = productPath.toFile();
        final Path releasePath = Paths.get(String.format(repoPath + "/" + release.getCpaas().getReleaseFile()));
        final File releaseFile = releasePath.toFile();
        if (productFile.exists()) {
            try {
                InputStream productIS = new FileInputStream(productFile);
                CPaaSProductFile cpaasProductFile = factory.createCPaaSProductFromStream(productIS);
                CPaaSProduct cpaasProduct = cpaasProductFile.getProduct();
                cpaasProduct.getRelease().setVersion(release.getVersion());
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
                factory.saveTo(cpaasProductFile, productFile, CPaaSProductFile.class);
                if (!release.isTestMode()) {
                    fileList.add(productFile);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            List<CVE> cveList = cveService.listCVEs(Optional.ofNullable(release.getVersion()), true);
            CPaaSReleaseFile cpaasReleaseFile = factory.createCPaaSReleaseFromTemplate(release.getVersion(), release.getPreviousVersion(), createAdvisory, cveList);
            factory.saveTo(cpaasReleaseFile, releaseFile, CPaaSReleaseFile.class);
            if (!release.isTestMode()) {
                fileList.add(releaseFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileList.stream();
    }


}
