package com.example.demo;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.MapUserDetailsRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer;
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerViewResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
@EnableMongoAuditing
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}

@Configuration
class WebConfig {

    @Autowired
    ApplicationContext ctx;

    @Bean
    public FreeMarkerConfigurer freeMarkerConfig() {
        FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
        configurer.setPreferFileSystemAccess(false);
        configurer.setTemplateLoaderPath("classpath:/templates/");
        configurer.setResourceLoader(this.ctx);
        return configurer;
    }

    @Bean
    public FreeMarkerViewResolver freeMarkerViewResolver() {
        final FreeMarkerViewResolver freeMarkerViewResolver = new FreeMarkerViewResolver("", ".ftl");
        freeMarkerViewResolver.setOrder(1);
        return freeMarkerViewResolver;
    }

}

@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    SecurityWebFilterChain springWebFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeExchange()
                .pathMatchers(HttpMethod.GET, "/posts/**").permitAll()
                .pathMatchers(HttpMethod.DELETE, "/posts/**").hasRole("ADMIN")
                .pathMatchers("/posts/**").authenticated()
                //.pathMatchers("/users/{user}/**").access(this::currentUserMatchesPath)
                .anyExchange().permitAll()
                .and()
                .build();
    }

    private Mono<AuthorizationDecision> currentUserMatchesPath(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .map(a -> context.getVariables().get("user").equals(a.getName()))
                .map(granted -> new AuthorizationDecision(granted));
    }

    @Bean
    public MapUserDetailsRepository userDetailsService() {
        UserDetails user = User.withUsername("test").password("password").roles("USER").build();
        UserDetails admin = User.withUsername("admin").password("password").roles("USER", "ADMIN").build();
        return new MapUserDetailsRepository(user, admin);
    }

}

@Component
@Slf4j
class DataInitializer implements CommandLineRunner {

    private final PostRepository posts;

    public DataInitializer(PostRepository posts) {
        this.posts = posts;
    }

    @Override
    public void run(String[] args) {
        log.info("start data initialization  ...");
        this.posts
                .deleteAll()
                .thenMany(
                        Flux.just("Post one", "Post two")
                                .flatMap(
                                        title -> this.posts.save(Post.builder().title(title).content("content of " + title).build())
                                )
                )
                .log()
                .subscribe(
                        null,
                        null,
                        () -> log.info("done initialization...")
                );

    }

}

@Controller
class HomeController {

    private final PostRepository posts;

    public HomeController(PostRepository posts) {
        this.posts = posts;
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("posts", this.posts.findAll().collectList().block(Duration.ofSeconds(100)));
        return "home";
    }

    @GetMapping("/hello")
    public String hello(Model model) {
        model.addAttribute("hello", "Hi, Freemarker");
        return "hello";
    }
}

@RestController()
@RequestMapping(value = "/posts")
class PostController {

    private final PostRepository posts;

    public PostController(PostRepository posts) {
        this.posts = posts;
    }

    @GetMapping("")
    public Flux<Post> all() {
        return this.posts.findAll();
    }

    @PostMapping("")
    public Mono<Post> create(@RequestBody Post post) {
        return this.posts.save(post);
    }

    @GetMapping("/{id}")
    public Mono<Post> get(@PathVariable("id") String id) {
        return this.posts.findById(id);
    }

    @PutMapping("/{id}")
    public Mono<Post> update(@PathVariable("id") String id, @RequestBody Post post) {
        return this.posts.findById(id)
                .map(
                        p -> {
                            p.setTitle(post.getTitle());
                            p.setContent(post.getContent());

                            return p;
                        }
                )
                .flatMap(p -> this.posts.save(p));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable("id") String id) {
        return this.posts.deleteById(id);
    }

}

interface PostRepository extends ReactiveMongoRepository<Post, String> {
}

@Document
@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Post {

    @Id
    private String id;
    private String title;
    private String content;

    @CreatedDate
    private LocalDateTime createdDate;
}
