package com.example.cqrs.http;

import com.opencqrs.framework.command.CommandRouter;
import com.example.cqrs.domain.UserAccount;
import com.example.cqrs.domain.api.command.ChangeEmailCommand;
import com.example.cqrs.domain.api.command.GetUserAccountCommand;
import com.example.cqrs.domain.api.command.SignUpCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/user-accounts")
public class UserController {

    private final CommandRouter commandRouter;

    public UserController(CommandRouter commandRouter) {
        this.commandRouter = commandRouter;
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserAccount> getAccount(@PathVariable String username) {
        try {
            return ResponseEntity.ok(commandRouter.send(new GetUserAccountCommand(username)));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody SignUpCommand command) {
        String username = command.username().toLowerCase();
        String email = command.email().toLowerCase();

        boolean successful = commandRouter.send(new SignUpCommand(username, email));

        if (successful) {
            return ResponseEntity.created(URI.create("/api/user-accounts/" + username)).build();
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
    }

    @PostMapping("/{username}/change-email")
    public ResponseEntity<Void> changeEmail(@PathVariable String username, @RequestBody ChangeEmailCommand command) {
        boolean successful = commandRouter.send(new ChangeEmailCommand(username, command.newEmail().toLowerCase()));

        if (successful) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
    }
}
