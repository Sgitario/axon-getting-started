package org.sgitario.axon.library.repository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.axonframework.modelling.command.Repository;
import org.axonframework.queryhandling.QueryHandler;
import org.sgitario.axon.library.aggregate.Library;
import org.sgitario.axon.library.queries.GetLibraryQuery;
import org.springframework.stereotype.Service;

@Service
public class LibraryProjector {
	private final Repository<Library> libraryRepository;

	public LibraryProjector(Repository<Library> libraryRepository) {
		this.libraryRepository = libraryRepository;
	}

	@QueryHandler
	public Library getLibrary(GetLibraryQuery query) throws InterruptedException, ExecutionException {
		CompletableFuture<Library> future = new CompletableFuture<Library>();
		libraryRepository.load("" + query.getLibraryId()).execute(future::complete);
		return future.get();
	}

}
