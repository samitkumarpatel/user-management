package net.samitkumar.user_management;

import org.springframework.boot.SpringApplication;

public class TestUserManagementApplication {

	public static void main(String[] args) {
		SpringApplication.from(UserManagementApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
