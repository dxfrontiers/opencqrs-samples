package com.example.cqrs.http;

import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import com.example.cqrs.domain.Book;
import com.example.cqrs.domain.api.command.GetBookCommand;
import com.example.cqrs.domain.api.command.PurchaseBookCommand;
import com.example.cqrs.domain.api.command.CorrectBookDetailsCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final CommandRouter commandRouter;
    private final BookCatalogProjection projection;

    public BookController(CommandRouter commandRouter, BookCatalogProjection projection) {
        this.commandRouter = commandRouter;
        this.projection = projection;
    }

    @PostMapping
    public ResponseEntity<Void> purchase(@RequestBody PurchaseBookCommand command) {
        commandRouter.send(command);
        return ResponseEntity.created(URI.create("/api/books/" + command.isbn())).build();
    }

    @PutMapping("/{isbn}")
    public ResponseEntity<Void> correctDetails(@PathVariable String isbn, @RequestBody MetadataRequest body) {
        commandRouter.send(new CorrectBookDetailsCommand(isbn, body.title(), body.authors(), body.version()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{isbn}")
    public ResponseEntity<Book> getBook(@PathVariable String isbn) {
        try {
            Book book = commandRouter.send(new GetBookCommand(isbn));
            return ResponseEntity.ok().eTag(book.version()).body(book);
        } catch (CommandSubjectDoesNotExistException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{isbn}/projected")
    public ResponseEntity<BookView> getProjected(@PathVariable String isbn) {
        return projection.findByIsbn(isbn)
                .map(view -> ResponseEntity.ok().eTag(view.version()).body(view))
                .orElse(ResponseEntity.notFound().build());
    }

    record MetadataRequest(String title, List<String> authors, String version) {}
}
