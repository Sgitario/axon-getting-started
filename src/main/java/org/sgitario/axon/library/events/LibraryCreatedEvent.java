package org.sgitario.axon.library.events;

import lombok.Data;

@Data
public class LibraryCreatedEvent {
	private final Integer libraryId;
	private final String name;
}
