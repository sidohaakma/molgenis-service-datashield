package org.molgenis.r.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.molgenis.r.exceptions.RExecutionException;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPNull;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RFileInputStream;
import org.rosuda.REngine.Rserve.RFileOutputStream;
import org.rosuda.REngine.Rserve.RserveException;
import org.springframework.core.io.Resource;
import org.springframework.security.util.InMemoryResource;

@ExtendWith(MockitoExtension.class)
class RExecutorServiceImplTest {

  private RExecutorServiceImpl executorService;
  @Mock private RConnection rConnection;
  @Mock private REXP rexp;
  @Mock private RFileOutputStream rFileOutputStream;
  @Mock private RFileInputStream rFileInputStream;

  @BeforeEach
  void before() {
    executorService = new RExecutorServiceImpl();
  }

  @Test
  void execute() throws RserveException {
    when(rConnection.eval("try({mean(age)})")).thenReturn(rexp);

    REXP result = executorService.execute("mean(age)", rConnection);

    assertSame(rexp, result);
  }

  @Test
  void executeFail() throws RserveException {
    when(rConnection.eval("try({mean(ages)})"))
        .thenThrow(new RExecutionException(new Exception("Ages is not a valid column")));

    RExecutionException rExecutionException =
        assertThrows(
            RExecutionException.class,
            () -> executorService.execute("mean(ages)", rConnection),
            "Ages is not a valid column");
    assertTrue(rExecutionException.getMessage().contains("Ages is not a valid column"));
  }

  @Test
  void executeTryFails() throws RserveException, REXPMismatchException {
    when(rConnection.eval("try({mean(age)})")).thenReturn(rexp);
    when(rexp.inherits("try-error")).thenReturn(true);
    when(rexp.asStrings())
        .thenReturn(new String[] {"Error in try(mean(age)) : object 'age' not found\n"});

    RExecutionException thrown =
        assertThrows(
            RExecutionException.class, () -> executorService.execute("mean(age)", rConnection));
    assertEquals("Error in try(mean(age)) : object 'age' not found", thrown.getMessage());
  }

  @Test
  void testLoadWorkspace() throws IOException, RserveException {
    when(rConnection.createFile(".RData")).thenReturn(rFileOutputStream);
    Resource resource = new InMemoryResource("Hello");

    executorService.loadWorkspace(rConnection, resource, ".TibbleEnv");

    verify(rConnection).eval("base::load(file='.RData', envir=.TibbleEnv)");
    verify(rConnection).eval("base::unlink('.RData')");
  }

  @Test
  void testLoadTableWithVariables() throws IOException, RserveException {
    when(rConnection.createFile("project_folder_table.parquet")).thenReturn(rFileOutputStream);
    Resource resource = new InMemoryResource("Hello");

    when(rConnection.eval(
            "try({is.null(base::assign('D', value={arrow::read_parquet('project_folder_table.parquet', col_select = c(\"col1\",\"col2\"))}))})"))
        .thenReturn(new REXPLogical(true));
    when(rConnection.eval("try({base::unlink('project_folder_table.parquet')})"))
        .thenReturn(new REXPNull());

    executorService.loadTable(
        rConnection, resource, "project/folder/table.parquet", "D", List.of("col1", "col2"));

    verify(rConnection)
        .eval(
            "try({is.null(base::assign('D', value={arrow::read_parquet('project_folder_table.parquet', col_select = c(\"col1\",\"col2\"))}))})");
    verify(rConnection).eval("try({base::unlink('project_folder_table.parquet')})");
  }

  @Test
  void testLoadTableNoVariables() throws IOException, RserveException {
    when(rConnection.createFile("project_folder_table.parquet")).thenReturn(rFileOutputStream);
    Resource resource = new InMemoryResource("Hello");

    when(rConnection.eval(
            "try({is.null(base::assign('D', value={arrow::read_parquet('project_folder_table.parquet')}))})"))
        .thenReturn(new REXPLogical(true));
    when(rConnection.eval("try({base::unlink('project_folder_table.parquet')})"))
        .thenReturn(new REXPNull());

    executorService.loadTable(
        rConnection, resource, "project/folder/table.parquet", "D", List.of());

    verify(rConnection)
        .eval(
            "try({is.null(base::assign('D', value={arrow::read_parquet('project_folder_table.parquet')}))})");
    verify(rConnection).eval("try({base::unlink('project_folder_table.parquet')})");
  }

  @Test
  void testSaveWorkspace() throws IOException, RserveException {
    when(rConnection.eval(
            "try({base::save(list = base::grep(\"^(?!\\\\Q.DSTableEnv\\\\E).*\", base::ls(all.names=T), perl=T, value=T), file=\".RData\")})"))
        .thenReturn(new REXPNull());
    when(rConnection.openFile(".RData")).thenReturn(rFileInputStream);

    executorService.saveWorkspace(
        "^(?!\\Q.DSTableEnv\\E).*",
        rConnection,
        inputStream -> assertSame(rFileInputStream, inputStream));

    verify(rConnection)
        .eval(
            "try({base::save(list = base::grep(\"^(?!\\\\Q.DSTableEnv\\\\E).*\", base::ls(all.names=T), perl=T, value=T), file=\".RData\")})");
    verify(rConnection).openFile(".RData");
  }
}
