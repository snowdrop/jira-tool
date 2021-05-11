/**
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package dev.snowdrop.release.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.snowdrop.release.model.cpaas.product.CPaaSProductFile;
import dev.snowdrop.release.model.cpaas.release.CPaaSReleaseFile;
import org.jboss.logging.Logger;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="antcosta@redhat.com">Antonio Costa</a>
 */
@Singleton
public class CPaaSReleaseFactory {

    static final Logger LOG = Logger.getLogger(CPaaSReleaseFactory.class);

    private static final YAMLMapper MAPPER = new YAMLMapper();

    static {
        MAPPER.disable(MapperFeature.AUTO_DETECT_CREATORS, MapperFeature.AUTO_DETECT_FIELDS,
                MapperFeature.AUTO_DETECT_GETTERS, MapperFeature.AUTO_DETECT_IS_GETTERS);
        final var factory = MAPPER.getFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public CPaaSProductFile createCPaaSProductFromStream(InputStream cpaasProductIs) throws IOException {
        CPaaSProductFile cpaaSProduct = MAPPER.readValue(cpaasProductIs, CPaaSProductFile.class);
        LOG.infof("Loaded cpaas product %s for release version %s", cpaaSProduct.getProduct().getName(), cpaaSProduct.getProduct().getRelease().getVersion());
        return cpaaSProduct;
    }

    public CPaaSReleaseFile createCPaaSReleaseFromStream(InputStream cpaasReleaseIs) throws IOException {
                CPaaSReleaseFile cpaaSRelease = MAPPER.readValue(cpaasReleaseIs, CPaaSReleaseFile.class);
        LOG.infof("Loaded cpaas release %s ", cpaaSRelease.getRelease().getName());
        return cpaaSRelease;
    }

    void saveTo(CPaaSProductFile release, File to) {
        final var writer = MAPPER.writerFor(CPaaSProductFile.class);
        try {
            writer.writeValue(to, release);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void saveTo(CPaaSReleaseFile release, File to) {
        final var writer = MAPPER.writerFor(CPaaSReleaseFile.class);
        try {
            writer.writeValue(to, release);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
