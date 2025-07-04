package net.samitkumar.user_management;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

import java.util.List;
import java.util.Objects;
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
						.GET("/filter", routerHandler::fetchFilteredUser)
						.GET("",routerHandler::allUser)
						.POST("", routerHandler::createNewUser)
						.GET("/{id}", routerHandler::getUserById)
						.PUT("/{id}", routerHandler::fullUpdate)
						.PATCH("/{id}", routerHandler::partialUpdate))
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
						.zipWith(
								userRepository.findAll().collectList(),
								(u1, u2) -> Stream.concat(
										u1.stream().map(u -> addUserType(u, UserType.EXTERNAL)),
										u2.stream().map(u -> addUserType(u, UserType.INTERNAL))
								)
						)
						.flatMap(ServerResponse.ok()::bodyValue);
	}

	private Stream<User> addUserTypeInUserStream(List<User> users, UserType type) {
		return users.stream()
				.map(user -> user.toBuilder().type(type).build());
	}

	private User addUserType(User user, UserType type) {
		return user.toBuilder().type(type).build();
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
				.map(user -> addUserType(user, UserType.EXTERNAL))
				.doOnNext(user -> log.info("jsonPlaceholderClient User: {}", user))
				.onErrorResume(e -> Mono.empty())
				.switchIfEmpty(Mono.empty());

		Mono<User> dbUser = userRepository.findById(userId)
				.map(user -> addUserType(user, UserType.INTERNAL))
				.doOnNext(user -> log.info("userRepository User: {}", user))
				.switchIfEmpty(Mono.empty());

		return externalUser
				.switchIfEmpty(dbUser)
				.flatMap(user -> ServerResponse.ok().bodyValue(user))
				.switchIfEmpty(ServerResponse.notFound().build());
	}

	public Mono<ServerResponse> fetchFilteredUser(ServerRequest request) {
		var userName = request.queryParam("username").orElseThrow(() -> new InvalidRequestException("Username query parameter is required"));
		log.info("Fetching /filter with username: {}", userName);
		return jsonPlaceholderClient
				.getUserByUsername(userName)
				.defaultIfEmpty(List.of())
				.map(users -> users.isEmpty() ? User.builder().build() : users.getFirst())
				.zipWith(userRepository.findByUsername(userName).defaultIfEmpty(User.builder().build()))
				.flatMap(tuple -> {
					User externalUser = tuple.getT1();
					User dbUser = tuple.getT2();
					if (Objects.nonNull(externalUser.id())) {
						var externalUserBuilder = externalUser.toBuilder();
						externalUserBuilder.type(UserType.EXTERNAL);
						return ServerResponse.ok().bodyValue(externalUserBuilder.build());
					} else if (Objects.nonNull(dbUser.id())) {
						var dbUserBuilder = dbUser.toBuilder();
						dbUserBuilder.type(UserType.INTERNAL);
						return ServerResponse.ok().bodyValue(dbUserBuilder.build());
					} else {
						return ServerResponse.notFound().build();
					}
				});
	}

	public Mono<ServerResponse> fullUpdate(ServerRequest request) {
		return null;
	}

	public Mono<ServerResponse> partialUpdate(ServerRequest request) {
		return null;
	}
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
class InvalidRequestException extends RuntimeException {
	InvalidRequestException(String message) {
		super(message);
	}
}

enum UserType { EXTERNAL, INTERNAL;}

@Document
@Builder(toBuilder = true)
record User(@MongoId String id, String name, String username, String email, Address address, @ReadOnlyProperty UserType type, Boolean active) {

	record Address(String street, String suite, String city, String zipcode, Geo geo) {
		record Geo(String lat, String lng) {}
	}
}

interface UserRepository extends ReactiveMongoRepository<User, String> {
	Mono<User> findByUsername(String username);
	Mono<List<User>> findByUsernameIsLike(String username);
}

@HttpExchange
interface JsonPlaceholderClient {
	@GetExchange("/users")
	Flux<User> getAllUser();

	@GetExchange("/users/{id}")
	Mono<User> getUserById(@PathVariable String id);

	@GetExchange("/users")
	Mono<List<User>> getUserByUsername(@RequestParam String username);
}
