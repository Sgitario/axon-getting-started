package org.sgitario.axon.library.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import lombok.Data;

@Data
public class RegisterBookCommand {
	@TargetAggregateIdentifier
	private final Integer libraryId;
	private final String isbn;
	private final String title;
}
