package com.selflearning.aws.sqs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration;
import org.springframework.cloud.aws.messaging.core.QueueMessagingTemplate;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = {ContextStackAutoConfiguration.class})
@RestController
public class SpringBootAwsSqsExampleApplication {

	Logger logger = LoggerFactory.getLogger(SpringBootAwsSqsExampleApplication.class);

	@Autowired
	private QueueMessagingTemplate queueMessagingTemplate;

	@Value("${cloud.aws.end-point.uri}")
	private String endpoint;

	@GetMapping("/send/{message}")
	public void sendMessageTOQueue(@PathVariable String message){
		queueMessagingTemplate.send(endpoint, MessageBuilder.withPayload(message).build());
	}

	@SqsListener("mytest-queue")
	public void receiveMessagesFromQueue(String message) {
		logger.info("message from SQS Queue {}",message);
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringBootAwsSqsExampleApplication.class, args);
	}

}