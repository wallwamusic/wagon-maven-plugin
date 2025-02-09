package org.codehaus.mojo.wagon.shared;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.apache.maven.wagon.CommandExecutor;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.StringUtils;

/**
 * @plexus.component role="org.codehaus.mojo.wagon.shared.WagonUpload" role-hint="default"
 */

public class DefaultWagonUpload implements WagonUpload {

    /**
     * @plexus.requirement role="org.codehaus.plexus.archiver.manager.ArchiverManager"
     */
    private ArchiverManager archiverManager;

    public void upload(Wagon wagon, FileSet fileset, Log logger)
            throws WagonException {

        FileSetManager fileSetManager = new FileSetManager(logger, logger.isDebugEnabled());

        String[] files = fileSetManager.getIncludedFiles(fileset);

        String url = wagon.getRepository().getUrl() + "/";

        if (files.length == 0) {
            logger.info("Nothing to upload.");
            return;
        }

        for (int i = 0; i < files.length; ++i) {
            String relativeDestPath = StringUtils.replace(files[i], "\\", "/");

            if (!StringUtils.isBlank(fileset.getOutputDirectory())) {
                relativeDestPath = fileset.getOutputDirectory() + "/" + relativeDestPath;
            }

            File source = new File(fileset.getDirectory(), files[i]);

            logger.info("Uploading " + source + " to " + url + relativeDestPath + " ...");

            wagon.put(source, relativeDestPath);
        }

    }

    public void upload(Wagon wagon, FileSet fileset, boolean optimize, Log logger)
            throws WagonException, IOException {
        if (!optimize) {
            upload(wagon, fileset, logger);
            return;
        }

        if (!(wagon instanceof CommandExecutor)) {
            throw new UnsupportedProtocolException("Wagon " + wagon.getRepository().getProtocol()
                    + " does not support optimize upload");
        }

        logger.info("Uploading " + fileset);

        File zipFile;
        zipFile = File.createTempFile("wagon", ".zip");

        try {
            FileSetManager fileSetManager = new FileSetManager(logger, logger.isDebugEnabled());
            String[] files = fileSetManager.getIncludedFiles(fileset);

            if (files.length == 0) {
                logger.info("Nothing to upload.");
                return;
            }

            logger.info("Creating " + zipFile + " ...");
            createZip(files, zipFile, fileset.getDirectory());

            String remoteFile = zipFile.getName();
            String remoteDir = fileset.getOutputDirectory();
            if (!StringUtils.isBlank(remoteDir)) {
                remoteFile = remoteDir + "/" + remoteFile;
            }

            logger.info("Uploading " + zipFile + " to " + wagon.getRepository().getUrl() + "/" + remoteFile + " ...");
            wagon.put(zipFile, remoteFile);

            // We use the super quiet option here as all the noise seems to kill/stall the connection
            String command = "unzip -o -qq -d " + remoteDir + " " + remoteFile;
            if (StringUtils.isBlank(remoteDir)) {
                command = "unzip -o -qq " + remoteFile;
            }

            try {
                logger.info("Remote: " + command);
                ((CommandExecutor) wagon).executeCommand(command);
            } finally {
                command = "rm -f " + remoteFile;
                logger.info("Remote: " + command);

                ((CommandExecutor) wagon).executeCommand(command);
            }

        } finally {
            zipFile.delete();
        }

    }

    private void createZip(String[] files, File zipFile, String basedir)
            throws IOException {
        try {
            ZipArchiver archiver = (ZipArchiver) this.archiverManager.getArchiver(zipFile);
            archiver.setDestFile(zipFile);
            for (String file : files) {
                archiver.addFile(new File(basedir, file), file);
            }
            archiver.createArchive();
        } catch (NoSuchArchiverException e) {
            // should never happen
        }
    }
}
