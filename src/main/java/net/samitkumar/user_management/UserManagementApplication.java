package net.samitkumar.user_management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Stream;

@SpringBootApplication
public class UserManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserManagementApplication.class, args);
	}

	@Bean
	JsonPlaceholderClient jsonPlaceholderClient(WebClient.Builder clientBuilder) {
		WebClientAdapter adapter = WebClientAdapter.create(clientBuilder.baseUrl("https://jsonplaceholder.typicode.com").build());
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
		return factory.createClient(JsonPlaceholderClient.class);
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction(RouterHandler routerHandler) {
		return RouterFunctions
				.route()
				.path("/user", builder -> builder
						.GET("",routerHandler::allUser)
						.POST("", routerHandler::createNewUser)
						.GET("/{id}", routerHandler::getUserById))
				.build();
	}
}

@Component
@RequiredArgsConstructor
@Slf4j
class RouterHandler {
	final JsonPlaceholderClient jsonPlaceholderClient;
	final UserRepository userRepository;

	public Mono<ServerResponse> allUser(ServerRequest request) {
		return
				jsonPlaceholderClient.getAllUser()
						.collectList()
						.zipWith(userRepository.findAll().collectList(), (u1, u2) -> {
							log.info("Fetched {} external users and {} internal users", u1.size(), u2.size());
							var externalUsers = u1.stream().map(user -> new User(user.id(), user.name(), user.username(), user.email(), user.address(), UserType.EXTERNAL));
							var internalUsers = u2.stream().map(user -> new User(user.id(), user.name(), user.username(), user.email(), user.address(), UserType.INTERNAL));
							return Stream.concat(externalUsers, internalUsers).toList();
						})
						.flatMap(ServerResponse.ok()::bodyValue);

		/*return Flux.merge(jsonPlaceholderClient.getAllUser(), userRepository.findAll())
				.collectList()
				.flatMap(ServerResponse.ok()::bodyValue);*/
	}

	public Mono<ServerResponse> createNewUser(ServerRequest request) {
		return request
				.bodyToMono(User.class)
				.flatMap(userRepository::save)
				.flatMap(ServerResponse.ok()::bodyValue);
	}

	public Mono<ServerResponse> getUserById(ServerRequest request) {
		var userId = request.pathVariable("id");
		log.info("Fetching user with ID: {}", userId);
		Mono<User> externalUser = jsonPlaceholderClient.getUserById(userId)
				.map(user -> new User(user.id(), user.name(), user.username(), user.email(), user.address(), UserType.EXTERNAL))
				.doOnNext(user -> log.info("jsonPlaceholderClient User: {}", user))
				.onErrorResume(e -> Mono.empty())
				.switchIfEmpty(Mono.empty());

		Mono<User> dbUser = userRepository.findById(userId)
				.map(user -> new User(user.id(), user.name(), user.username(), user.email(), user.address(), UserType.INTERNAL))
				.doOnNext(user -> log.info("userRepository User: {}", user))
				.switchIfEmpty(Mono.empty());

		return externalUser
				.switchIfEmpty(dbUser)
				.flatMap(user -> ServerResponse.ok().bodyValue(user))
				.switchIfEmpty(ServerResponse.notFound().build());
	}
}
enum UserType { EXTERNAL, INTERNAL;}

@Document
record User(@MongoId String id, String name, String username, String email, Address address, @ReadOnlyProperty UserType type) {
	record Address(String street, String suite, String city, String zipcode, Geo geo) {
		record Geo(String lat, String lng) {}
	}
}

interface UserRepository extends ReactiveMongoRepository<User, String> {}

@HttpExchange
interface JsonPlaceholderClient {
	@GetExchange("/users")
	Flux<User> getAllUser();

	@GetExchange("/users/{id}")
	Mono<User> getUserById(@PathVariable String id);

	@GetExchange("/users")
	Mono<User> getUserByUsername(@RequestParam String username);
}
