---
layout: post
title: Axon - CQRS with Spring Boot by examples
date: 2018-10-23
tags: [ Axon, Java, Architecture ]
---

We already introduced [CQRS](https://martinfowler.com/bliki/CQRS.html) architectures a while ago [here](https://sgitario.github.io/applying-event-sourcing-and-qcrs-in/). The focus was to migrate a legacy monolith application into a CQRS architecture. However, we didn't go in deep by actually coding it. Let's do this using [Axon Framework](https://axoniq.io/) and Spring Boot. We'll create a library application from scratch.

# Some Concepts First

[Axon](https://axoniq.io/) is not just a framework, but an infrastructure that also involves an Axon server. The Axon server manages a bus event and all the mecanisms to manage the commands and queries:

![Axon Architecture]({{ site.url }}{{ site.baseurl }}/images/axon-architecture-1.png)

This image is taken from [the official Axon documentation](https://docs.axoniq.io/reference-guide/architecture-overview) for version 4.

## Event Store

Where all our events will be stored? In the Event Store running under the Axon Server (see *AxonServerEventStore.java* for more information). If we want to use an embedded event store in, let's say, a RDBMS instance or in Mongo, Axon provides custom implementations for this. More in [here](https://docs.axoniq.io/reference-guide/1.3-infrastructure-components/repository-and-event-store#jdbceventstorageengine). This is something we'll like to explore further in the future.

## Aggregates

Aggregates are the domain objects in Axon. We can configure our configuration to match the aggregate to an entity in our database. 

When we connect our application, Events will be read on-demand when an existing aggregate is targeted by a command. To enhance efficiency when processing multiple commands to the same aggregate, Axon supports caching repositories that would keep the aggregate in memory to avoid another read of the same events. Once aggregates are totally synchronized, it will be ready to be used. See [*AggregateLifecycle.java*](https://axoniq.io/apidocs/3.1/org/axonframework/commandhandling/model/AggregateLifecycle.html).

# Getting Started

## Run Axon Server

We'll use [the official docker image](https://hub.docker.com/r/axoniq/axonserver/) to startup an Axon server instance:

```
docker run -d --name axonserver -p 8024:8024 -p 8124:8124 axoniq/axonserver
```

Feel free to startup a server instance yourself by downloading the binaries from [here](http://www.axonframework.org/download). 

In order to check the installation was succeded, browse to "http://localhost:8024/" and we should see the Axon dashboard:

![Axon Dashboard]({{ site.url }}{{ site.baseurl }}/images/axon-installation-1.png)

## Axon Framework: Maven Dependencies

We'll use [the Axon Spring Boot Starter maven dependency](https://mvnrepository.com/artifact/org.axonframework/axon-core/4.0-M2) in the *pom.xml* for all our projects:

```xml
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring-boot-starter</artifactId>
    <version>4.0</version>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>2.0.6.RELEASE</version>
</dependency>
```

This is the easiest way to get warm with Axon. Spring Boot eases the configuration using default components in Axon. For more information, go to [here](https://docs.axoniq.io/reference-guide/1.3-infrastructure-components/spring-boot-autoconfiguration).

Also, Axon provides a very good tutorial or [recipe with Spring Boot](https://docs.axoniq.io/axon-cookbook/basic-recipes/simple-application-using-axon-framework-and-spring-boot).

# The Library Application

Let's start coding! We'll write a library application where we can organize books into different libraries. 

![Diagram]({{ site.url }}{{ site.baseurl }}/images/axon-bookstore-diagram.jpg)

We need to start thinking on events. What is a library? In object oriented programming, a library is just a set of books. In event oriented programming, a library is: 

- event 1: library named "My Library"
- event 2: Added book "x" to "My Library"
- event 3: Added book "y" to "My Library"

Note, we can redefine the concept of libraries anytime by adding a new event. And the most important, we have a time-scale of our library thanks to events.

On the other hand, we have the commands. A command is an use case in our application and it can derivate in a set of events that will define the current state. 

Therefore, let's continue coding our application.

## Application: Spring Boot

We'll use Spring Boot which is the easiest way to startup our application:

```java
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
```

**This is only for testing purposes.** For production, we'd need to provide a persistence state of the event store and use a distributed queue. We'll write about these refinements in a future post.

## Commands: Use Cases

The commands are the use cases of our application and needs to verify the data is correct before adding an event. Therefore, we don't need to validate the events afterwards. 

**- Register a Library**

Let's define the command to register a new library:

```java
public class RegisterLibraryCommand {
	@TargetAggregateIdentifier
	private final Integer libraryId;

	private final String name;

    // Constructor and getters
}
```

We'll identify our libraries by an integer and a name. 

Let's start writing the Library aggregate:

```java
@Aggregate
public class Library {

	@AggregateIdentifier
	private Integer libraryId;
	private String name;
	private List<String> isbnBooks;

	protected Library() {
		// For Axon instantiation
	}

	@CommandHandler
	public Library(RegisterLibraryCommand cmd) {
		Assert.notNull(cmd.getLibraryId(), "ID should not be null");
		Assert.notNull(cmd.getName(), "Name should not be null");

		AggregateLifecycle.apply(new LibraryCreatedEvent(cmd.getLibraryId(), cmd.getName()));
	}

	// getters
}
```

Axon Spring Boot starter will scan for classes annotated with *@Aggregate* and register it into the Axon application. The same for methods annotated with *@CommandHandler* where the Axon framework will invoke them after trigger a command of the matching type. 

The constructor with the *RegisterLibraryCommand* command states the aggregate will be created using this command. This can be also achieved via:

```java
AggregateLifecycle.createNew(Library.class, Library::new);
```

The other protected constructor is used by Axon to instantiate existing aggregates from events. 

And as we already said, the events define the state of the aggregates, so we need to register this creation with a new event:

```java
public class BookCreatedEvent {
	@TargetAggregateIdentifier
	private final Integer libraryId;
	private final String isbn;
	private final String title;

    // constructor and getters
}
```

Hold on... Something is missing... where are we setting the library data? Not in the command handler: we need to validate the data in the commands, but we should not set the data here. Why? As we already said, after our application started and joined to the Axon server, it will synchronize the events in the server and create the required aggregates. Therefore, we need to set the data when handling the events:

```java
@Aggregate
public class Library {

	@AggregateIdentifier
	private Integer libraryId;
	private String name;
	private List<String> isbnBooks;

	protected Library() {
		// For Axon instantiation
	}

	@CommandHandler
	public Library(RegisterLibraryCommand cmd) {
		Assert.notNull(cmd.getLibraryId(), "ID should not be null");
		Assert.notNull(cmd.getName(), "Name should not be null");

		AggregateLifecycle.apply(new LibraryCreatedEvent(cmd.getLibraryId(), cmd.getName()));
	}

	// getters

	@EventSourcingHandler
	private void handleCreatedEvent(LibraryCreatedEvent event) {
		libraryId = event.getLibraryId();
		name = event.getName();
		isbnBooks = new ArrayList<>();
	}

}
```

We annotate the methods with *@EventSourcingHandler* to listen events of a concrete type and that manipulate the aggregates. **Don't confuse with the annotation *@EventHandler* which is used outside of an aggregate class. We'll see an example later.**

**- Register a Book**

"I want to add a new book to my library". Let's write this command:

```java
public class RegisterBookCommand {
	@TargetAggregateIdentifier
	private final Integer libraryId;
	private final String isbn;
	private final String title;

    // constructor and getters
}
```

And let's update our aggregate:

```java
@Aggregate
public class Library {

	@AggregateIdentifier
	private Integer libraryId;
	private String name;
	private List<String> isbnBooks;

	// constructors

	// getters

    // handleCreatedEvent command

	@CommandHandler
	public void addBook(RegisterBookCommand cmd) {
		Assert.notNull(cmd.getLibraryId(), "ID should not be null");
		Assert.notNull(cmd.getIsbn(), "Book ISBN should not be null");

		AggregateLifecycle.apply(new BookCreatedEvent(cmd.getLibraryId(), cmd.getIsbn(), cmd.getTitle()));
	}

	@EventSourcingHandler
	private void addBook(BookCreatedEvent event) {
		isbnBooks.add(event.getIsbn());
	}

}
```

Where *BookCreatedEvent* class is:

```java
public class BookCreatedEvent {
	@TargetAggregateIdentifier
	private final Integer libraryId;
	private final String isbn;
	private final String title;

    // constructor and getters
}
```

Nothing new so far, but wait a second: we're only using the ISBN book identifier in the aggregate, so we're losing the title! Kind of. This is because we wanted to use another repository to store the book data by listening to the *BookCreatedEvent* event outside of the aggregate.

We'll use a JPA repository and H2 as an in-memory database. The only thing we need to add is to add these two maven dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>2.0.6.RELEASE</version>
</dependency>

<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>1.4.197</version>
</dependency>
```

Then, create the repository using Spring Data:

```java
@Repository
public interface BookRepository extends CrudRepository<BookEntity, String> {
	List<BookEntity> findByLibraryId(Integer libraryId);
}
```

And finally, add a service to listen events using the *@EventHandler* annotation:

```java
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
}
```

And that's all! 

## Queries

We are done with the use cases and commands. We'll work now about how to expose our data outside using the *@QueryHandler* annotation. 

**Important: *@QueryHandler* annotations do not work on aggregate classes.**

**- Query List of Books**

If we own the repository where our data is stored, we could only read the data from the repository provided by Spring Data:

```java
@Service
public class BookRepositoryProjector {

	private final BookRepository bookRepository;

	// constructor

	// add book method

	@QueryHandler
	public List<BookBean> getBooks(GetBooksQuery query) {
		return bookRepository.findByLibraryId(query.getLibraryId()).stream()
            .map(e -> {
                BookBean book = new BookBean();
                book.setIsbn(e.getIsbn());
                book.setTitle(e.getTitle());
                return book;
            }).collect(Collectors.toList());
	}
}
```

Where the *GetBooksQuery* class is:

```java
public class GetBooksQuery {
	private final Integer libraryId;

    // constructor and getter
}
```

**- Get Library aggregate**

However, most of the times we will want the current state of an aggregate since this would be the view of our business. We need to instantiate the *Repository* of the Event Store: where the aggregates are. Thanks to Spring, the correct implementation is already in the application context and all we need is:

```java
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
```

Where the *GetLibraryQuery* class is:

```java
public class GetLibraryQuery {
	private final Integer libraryId;

    // constructor and getter
}
```

Something odd is that the *load* method only accepts String for the aggregate identifiers and we use an integer. That's why we needed to cast it to String. 

## REST layer

Right, we now know how to deal with command and queries, but how can we invoke or send these commands or events? 

Axon provides a *CommandGateway* interface to trigger commands:

```java
commandGateway.send(new MyCommand(...));
```

And it also provides a *QueryGateway* interface to trigger queries:

```java
CompletableFuture<Library> future = queryGateway.query(new MyQuery(...), Library.class);
return future.get();
```

Therefore, this is how would look like our REST API:

```java
@RestController
public class LibraryRestController {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;

	@Autowired
	public LibraryRestController(CommandGateway commandGateway, QueryGateway queryGateway) {
		this.commandGateway = commandGateway;
		this.queryGateway = queryGateway;
	}

	@PostMapping("/api/library")
	public String addLibrary(@RequestBody LibraryBean library) {
		commandGateway.send(new RegisterLibraryCommand(library.getLibraryId(), library.getName()));
		return "Saved";
	}

	@GetMapping("/api/library/{library}")
	public Library getLibrary(@PathVariable Integer library) throws InterruptedException, ExecutionException {
		return queryGateway.query(new GetLibraryQuery(library), Library.class).get();
	}

	@PostMapping("/api/library/{library}/book")
	public String addBook(@PathVariable Integer library, @RequestBody BookBean book) {
		commandGateway.send(new RegisterBookCommand(library, book.getIsbn(), book.getTitle()),
				LoggingCallback.INSTANCE);
		return "Added";
	}

	@GetMapping("/api/library/{library}/book")
	public List<BookBean> addBook(@PathVariable Integer library) throws InterruptedException, ExecutionException {
		return queryGateway.query(new GetBooksQuery(library), ResponseTypes.multipleInstancesOf(BookBean.class)).get();
	}

}
```

## Run

We can run our application as an usual Spring Boot application. If you see the following in the output:

```
**********************************************
*                                            *
*  !!! UNABLE TO CONNECT TO AXON SERVER !!!  *
*                                            *
* Are you sure it's running?                 *
* If you haven't got Axon Server yet, visit  *
*       https://axoniq.io/download           *
*                                            *
**********************************************
```

This message means we cannot connect with the Axon server. Double check the Axon server is up and running and/or go to the Axon dashboard in the browser. 

- Add a library:

```
POST: http://localhost:8080/api/library
BODY:
{
	"libraryId": 1,
	"name": "My Library"
}
```

- Add a book:

```
POST: http://localhost:8080/api/library/1/book
BODY:
{
	"isbn":"123460",
	"title": "My Title",
	"author": "Jose Carvajal"
}
```

- Get books in a library:

```
GET: http://localhost:8080/api/library/1/book
```

- Get Library:

```
GET: http://localhost:8080/api/library/1
```

# Conclusion

We have introduced some of the most important features in Axon and how Axon works. Axon Framework makes extremely easy to implement a CQRS architecture and is very well integrated to Spring and Spring Cloud (for doing the services discoverable). Also, Axon is **VERY** customisable. We can refine until the most tiny component in your architecture which demostrates what a well designed the framework is. 

On the other side, even when the documentation is quite extensive and good, I would have appreciated working examples for version 4 of basic functionality. It does not help either when the Axon documentation redirects to the first chapter when you search something in google. 

See [my Github repository](https://github.com/Sgitario/axon-getting-started) for a full example.