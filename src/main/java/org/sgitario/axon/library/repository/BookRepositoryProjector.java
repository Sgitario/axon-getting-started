package org.sgitario.axon.library.repository;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.sgitario.axon.library.events.BookCreatedEvent;
import org.sgitario.axon.library.models.BookBean;
import org.sgitario.axon.library.queries.GetBooksQuery;
import org.springframework.stereotype.Service;

@Service
public class BookRepositoryProjector {

	private final BookRepository bookRepository;

	public BookRepositoryProjector(BookRepository bookRepository) {
		this.bookRepository = bookRepository;
	}

	@EventHandler
	public void addBook(BookCreatedEvent event) throws Exception {
		BookEntity book = new BookEntity();
		book.setIsbn(event.getIsbn());
		book.setLibraryId(event.getLibraryId());
		book.setTitle(event.getTitle());
		bookRepository.save(book);
	}

	@QueryHandler
	public List<BookBean> getBooks(GetBooksQuery query) {
		return bookRepository.findByLibraryId(query.getLibraryId()).stream().map(toBook()).collect(Collectors.toList());
	}

	private Function<BookEntity, BookBean> toBook() {
		return e -> {
			BookBean book = new BookBean();
			book.setIsbn(e.getIsbn());
			book.setTitle(e.getTitle());
			return book;
		};
	}
}
