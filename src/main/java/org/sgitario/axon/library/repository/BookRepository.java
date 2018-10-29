package org.sgitario.axon.library.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookRepository extends CrudRepository<BookEntity, String> {
	List<BookEntity> findByLibraryId(Integer libraryId);
}
