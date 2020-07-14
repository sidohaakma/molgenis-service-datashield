package org.molgenis.armadillo.minio;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.molgenis.armadillo.exceptions.StorageException;
import org.molgenis.armadillo.model.Workspace;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

  private MinioStorageService minioStorageService;
  @Mock private MinioClient minioClient;
  @Mock private InputStream inputStream;
  @Mock private Result<Item> itemResult;
  @Mock private Item item;

  @BeforeEach
  void beforeEach() {
    minioStorageService = new MinioStorageService(minioClient);
  }

  @Test
  void testCheckBucketExistsThrowsExceptionIfMinioDown() throws Exception {
    doThrow(new IOException("blah")).when(minioClient).bucketExists("bucket");

    assertThrows(StorageException.class, () -> minioStorageService.checkBucketExists("bucket"));
  }

  @Test
  void testCheckBucketExistsCreatesBucketIfNotFound() throws Exception {
    when(minioClient.bucketExists("bucket")).thenReturn(false);

    minioStorageService.checkBucketExists("bucket");

    verify(minioClient).makeBucket("bucket");
  }

  @Test
  void save() throws Exception {
    minioStorageService.save(inputStream, "bucket", "asdf.blah", APPLICATION_OCTET_STREAM);

    verify(minioClient)
        .putObject(
            "bucket", "asdf.blah", inputStream, null, null, null, APPLICATION_OCTET_STREAM_VALUE);
  }

  @Test
  void saveThrowsException() throws Exception {
    IOException exception = new IOException("blah");
    doThrow(exception)
        .when(minioClient)
        .putObject(
            "bucket", "asdf.blah", inputStream, null, null, null, APPLICATION_OCTET_STREAM_VALUE);

    assertThrows(
        StorageException.class,
        () ->
            minioStorageService.save(inputStream, "bucket", "asdf.blah", APPLICATION_OCTET_STREAM));
  }

  @Test
  void testListWorkspacesNoBucket() {
    assertEquals(emptyList(), minioStorageService.listWorkspaces("user-admin"));
  }

  @Test
  void testListWorkspaces() throws Exception {
    Instant lastModified = Instant.now().truncatedTo(MILLIS);
    Workspace workspace =
        Workspace.builder()
            .setName("blah")
            .setLastModified(lastModified)
            .setETag("\"abcde\"")
            .setSize(56)
            .build();

    when(minioClient.bucketExists("user-admin")).thenReturn(true);
    when(minioClient.listObjects("user-admin")).thenReturn(List.of(itemResult));
    when(itemResult.get()).thenReturn(item);
    when(item.objectName()).thenReturn("blah.RData");
    when(item.lastModified()).thenReturn(Date.from(lastModified));
    when(item.etag()).thenReturn(workspace.eTag());
    when(item.objectSize()).thenReturn(workspace.size());

    assertEquals(List.of(workspace), minioStorageService.listWorkspaces("user-admin"));
  }

  @Test
  void testLoad() throws Exception {
    when(minioClient.getObject("user-admin", "blah.RData")).thenReturn(inputStream);

    assertSame(inputStream, minioStorageService.load("user-admin", "blah.RData"));
  }

  @Test
  void testDelete() throws Exception {
    minioStorageService.delete("user-admin", "blah.RData");

    verify(minioClient).removeObject("user-admin", "blah.RData");
  }
}
