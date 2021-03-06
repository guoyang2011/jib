/*
 * Copyright 2017 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.tar;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link TarStreamBuilder}. */
public class TarStreamBuilderTest {

  private Path fileA;
  private Path fileB;
  private Path directoryA;
  private String fileAContents;
  private String fileBContents;
  private TarStreamBuilder testTarStreamBuilder = new TarStreamBuilder();

  @Before
  public void setup() throws URISyntaxException, IOException {
    // Gets the test resource files.
    fileA = Paths.get(Resources.getResource("fileA").toURI());
    fileB = Paths.get(Resources.getResource("fileB").toURI());
    directoryA = Paths.get(Resources.getResource("directoryA").toURI());

    fileAContents = new String(Files.readAllBytes(fileA), StandardCharsets.UTF_8);
    fileBContents = new String(Files.readAllBytes(fileB), StandardCharsets.UTF_8);
  }

  @Test
  public void testToBlob_tarArchiveEntries() throws IOException {
    setUpWithTarEntries();
    verifyBlobWithoutCompression();
  }

  @Test
  public void testToBlob_strings() throws IOException {
    setUpWithStrings();
    verifyBlobWithoutCompression();
  }

  @Test
  public void testToBlob_stringsAndTarArchiveEntries() throws IOException {
    setUpWithStringsAndTarEntries();
    verifyBlobWithoutCompression();
  }

  @Test
  public void testToBlob_tarArchiveEntriesWithCompression() throws IOException {
    setUpWithTarEntries();
    verifyBlobWithCompression();
  }

  @Test
  public void testToBlob_stringsWithCompression() throws IOException {
    setUpWithStrings();
    verifyBlobWithCompression();
  }

  @Test
  public void testToBlob_stringsAndTarArchiveEntriesWithCompression() throws IOException {
    setUpWithStringsAndTarEntries();
    verifyBlobWithCompression();
  }

  /** Creates a TarStreamBuilder using TarArchiveEntries. */
  private void setUpWithTarEntries() {
    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addEntry(
        new TarArchiveEntry(fileA.toFile(), "some/path/to/resourceFileA"));
    testTarStreamBuilder.addEntry(new TarArchiveEntry(fileB.toFile(), "crepecake"));
    testTarStreamBuilder.addEntry(new TarArchiveEntry(directoryA.toFile(), "some/path/to"));
    testTarStreamBuilder.addEntry(
        new TarArchiveEntry(
            fileA.toFile(),
            "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890"));
  }

  /** Creates a TarStreamBuilder using Strings. */
  private void setUpWithStrings() {
    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addEntry(fileAContents, "some/path/to/resourceFileA");
    testTarStreamBuilder.addEntry(fileBContents, "crepecake");
    testTarStreamBuilder.addEntry(new TarArchiveEntry(directoryA.toFile(), "some/path/to"));
    testTarStreamBuilder.addEntry(
        fileAContents,
        "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890");
  }

  /** Creates a TarStreamBuilder using Strings and TarArchiveEntries. */
  private void setUpWithStringsAndTarEntries() {
    // Prepares a test TarStreamBuilder.
    testTarStreamBuilder.addEntry(fileAContents, "some/path/to/resourceFileA");
    testTarStreamBuilder.addEntry(new TarArchiveEntry(fileB.toFile(), "crepecake"));
    testTarStreamBuilder.addEntry(new TarArchiveEntry(directoryA.toFile(), "some/path/to"));
    testTarStreamBuilder.addEntry(
        fileAContents,
        "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890");
  }

  /** Creates a compressed blob from the TarStreamBuilder and verifies it. */
  private void verifyBlobWithCompression() throws IOException {
    Blob blob = testTarStreamBuilder.toBlob();

    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    OutputStream compressorStream = new GZIPOutputStream(tarByteOutputStream);
    blob.writeTo(compressorStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    InputStream tarByteInputStream = new GZIPInputStream(byteArrayInputStream);
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(tarByteInputStream);
    verifyTarArchive(tarArchiveInputStream);
  }

  /** Creates an uncompressed blob from the TarStreamBuilder and verifies it. */
  private void verifyBlobWithoutCompression() throws IOException {
    Blob blob = testTarStreamBuilder.toBlob();

    // Writes the BLOB and captures the output.
    ByteArrayOutputStream tarByteOutputStream = new ByteArrayOutputStream();
    blob.writeTo(tarByteOutputStream);

    // Rearrange the output into input for verification.
    ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(tarByteOutputStream.toByteArray());
    TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(byteArrayInputStream);
    verifyTarArchive(tarArchiveInputStream);
  }

  /**
   * Helper method to verify that the files were archived correctly by reading {@code
   * tarArchiveInputStream}.
   */
  private void verifyTarArchive(TarArchiveInputStream tarArchiveInputStream) throws IOException {
    // Verifies fileA was archived correctly.
    TarArchiveEntry headerFileA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/resourceFileA", headerFileA.getName());
    String fileAString =
        CharStreams.toString(new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
    Assert.assertEquals(fileAContents, fileAString);

    // Verifies fileB was archived correctly.
    TarArchiveEntry headerFileB = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("crepecake", headerFileB.getName());
    String fileBString =
        CharStreams.toString(new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
    Assert.assertEquals(fileBContents, fileBString);

    // Verifies directoryA was archived correctly.
    TarArchiveEntry headerDirectoryA = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals("some/path/to/", headerDirectoryA.getName());

    // Verifies the long file was archived correctly.
    TarArchiveEntry headerFileALong = tarArchiveInputStream.getNextTarEntry();
    Assert.assertEquals(
        "some/really/long/path/that/exceeds/100/characters/abcdefghijklmnopqrstuvwxyz0123456789012345678901234567890",
        headerFileALong.getName());
    String fileALongString =
        CharStreams.toString(new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
    Assert.assertEquals(fileAContents, fileALongString);

    Assert.assertNull(tarArchiveInputStream.getNextTarEntry());
  }
}
