/*
 * Copyright 2018 Christian Fränkel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lessqq.hprof.remapper;

import com.badoo.hprof.library.HprofProcessor;
import com.badoo.hprof.library.HprofReader;
import com.beust.jcommander.*;
import com.beust.jcommander.converters.PathConverter;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Remapper {
    public static final Logger logger = Logger.getLogger(Remapper.class.getName());
    private final Options options;

    public Remapper(Options options) {
        this.options = options;
    }

    public static void main(String[] args) throws IOException {
        initLogging();
        Options options = new Options();
        JCommander jcmd = new JCommander(options);
        jcmd.setProgramName("bin/hprof-remap");
        try {
            jcmd.parse(args);
            if (options.inputFile.toAbsolutePath().equals(options.outputFile.toAbsolutePath()))
                throw new ParameterException("Input and output files must be different.");
        } catch (ParameterException e) {
            logger.severe(e.getMessage());
            StringBuilder sb = new StringBuilder();
            jcmd.usage(sb);
            logger.severe(sb.toString());
            System.exit(1);
        }
        if (options.isHelp) {
            StringBuilder sb = new StringBuilder();
            jcmd.usage(sb);
            logger.info(sb.toString());
            System.exit(0);
        }
        new Remapper(options).remapHeapDump();
    }

    public void remapHeapDump() {
        logger.info("HPROF Remapper version " + getVersion());
        logger.info("Configuration:");
        logger.info("Mapping Location: " + options.mappingLocation.toString());
        logger.info("Input file      : " + options.inputFile.toString());
        logger.info("Output file     : " + options.outputFile.toString());
        Path realMappingLocation = options.mappingLocation;
        if (options.mappingLocation.toFile().isFile()) {
            // try to unpack it
            try {
                File tempDir = File.createTempFile("mappings", "");
                realMappingLocation = tempDir.toPath();
                tempDir.delete();
                tempDir.mkdir();
                tempDir.deleteOnExit();
                ZipFile zip = new ZipFile(options.mappingLocation.toFile());
                extractFileFromZip(realMappingLocation, zip, "methods.csv");
                extractFileFromZip(realMappingLocation, zip, "fields.csv");
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read mapping zip", e);
            }
        }

        Map<String, String> methodMapping = readMapping(realMappingLocation.resolve("methods.csv"));
        Map<String, String> fieldMapping = readMapping(realMappingLocation.resolve("fields.csv"));

        try (InputStream input = Files.newInputStream(options.inputFile); OutputStream output = Files.newOutputStream(options.outputFile)) {
            HprofReader reader = new HprofReader(input, new RepmappingHprofProcessor(output, methodMapping, fieldMapping));
            while (reader.hasNext()) {
                reader.next();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remap heap dump", e);
        }
    }

    private void extractFileFromZip(Path realMappingLocation, ZipFile zip, String fieldName) throws IOException {
        ZipEntry entry = zip.getEntry(fieldName);
        Path target = realMappingLocation.resolve(fieldName);
        Files.copy(zip.getInputStream(entry), target);
        target.toFile().deleteOnExit();
    }

    private String getVersion() {
        String version = Remapper.class.getPackage().getImplementationVersion();
        if (version == null)
            return "DEVELOPMENT";
        return version;
    }

    private static void initLogging() {
        try (InputStream is = Remapper.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load logging.properties", e);
        }

    }

    private static Map<String, String> readMapping(Path mapping) {
        Map<String, String> result = new HashMap<>();
        try (Stream<String> lines = Files.lines(mapping);) {
            lines.forEach(line -> {
                String[] parts = line.split(",", 3);
                result.put(parts[0], parts[1]);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load mapping from " + mapping.toString(), e);
        }
        logger.info(() -> "Loaded " + result.size() + " mappings.");
        return result;
    }

    @Parameters(resourceBundle = "net.lessqq.hprof.remapper.messages")
    public static class Options {
        @Parameter(names = "-m",
                descriptionKey = "mappingLocation.desc",
                converter = PathConverter.class,
                validateValueWith = PathExistsValidator.class,
                required = true)
        public Path mappingLocation;
        @Parameter(names = "-i",
                descriptionKey = "inputFile.desc",
                converter = PathConverter.class,
                validateValueWith = PathExistsValidator.class,
                required = true)
        public Path inputFile;
        @Parameter(names = "-o",
                descriptionKey = "outputFile.desc",
                converter = PathConverter.class,
                required = true)
        public Path outputFile;

        @Parameter(names = {"-h", "--help"}, descriptionKey = "help.desc", help = true)
        public boolean isHelp = false;
    }

    public static class PathExistsValidator implements IValueValidator<Path> {
        @Override
        public void validate(String name, Path value) throws ParameterException {
            if (!value.toFile().exists())
                throw new ParameterException("Supplied argument '" + value + "' for parameter '" + name + "' does not exist.");
        }
    }
}
