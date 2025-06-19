package net.samitkumar.user_management;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Component;
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

import java.util.Map;
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
						.POST("", routerHandler::createNewUser))
				.build();
	}
}

@Component
@RequiredArgsConstructor
class RouterHandler {
	final JsonPlaceholderClient jsonPlaceholderClient;
	final UserRepository userRepository;

	public Mono<ServerResponse> allUser(ServerRequest request) {
		return Flux.merge(jsonPlaceholderClient.getAllUser(),userRepository.findAll())
				.collectList()
				.flatMap(ServerResponse.ok()::bodyValue);
	}

	public Mono<ServerResponse> createNewUser(ServerRequest request) {
		return request
				.bodyToMono(User.class)
				.flatMap(userRepository::save)
				.flatMap(ServerResponse.ok()::bodyValue);
	}
}

@Document
record User(@MongoId String id, String name, String username, String email, Address address) {
	record Address(String street, String suite, String city, String zipcode, Geo geo) {
		record Geo(String lat, String lng) {}
	}
}

interface UserRepository extends ReactiveMongoRepository<User, String> {}

@HttpExchange
interface JsonPlaceholderClient {
	@GetExchange("/users")
	Flux<User> getAllUser();
}
