package org.sgitario.axon.library.repository;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Data;

@Entity
@Data
public class BookEntity {
	@Id
	private String isbn;
	@Column
	private int libraryId;
	@Column
	private String title;
}
