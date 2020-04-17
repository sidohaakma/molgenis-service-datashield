package org.molgenis.datashield.command;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.molgenis.r.model.RPackage;
import org.rosuda.REngine.REXP;

public interface Commands {

  CompletableFuture<REXP> evaluate(String expression);

  CompletableFuture<Void> assign(String symbol, String expression);

  CompletableFuture<Void> loadWorkspace(String objectName, String environment);

  CompletableFuture<Void> saveWorkspace(String objectname);

  CompletableFuture<List<RPackage>> getPackages();

  Optional<CompletableFuture<REXP>> getLastExecution();

  Optional<DataShieldCommandDTO> getLastCommand();

  enum DataShieldCommandStatus {
    COMPLETED,
    FAILED,
    PENDING,
    IN_PROGRESS
  }
}
