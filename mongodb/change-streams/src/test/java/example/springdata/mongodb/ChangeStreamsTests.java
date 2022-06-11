/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.springdata.mongodb;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;
import static org.springframework.data.mongodb.core.query.Update.*;

import reactor.core.Disposable;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.bson.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.test.context.junit4.SpringRunner;

import com.mongodb.client.model.changestream.ChangeStreamDocument;

/**
 * A simple Test demonstrating required {@link Configuration} for consumption of MongoDB
 * <a href="https://docs.mongodb.com/manual/changeStreams/">Change Streams</a> using the sync and reactivestreams java
 * driver.
 * <p />
 * We currently need to have both tests in one class due to some shutdown error in flapdoodle which lets us start the
 * process only once.
 *
 * @author Christoph Strobl
 */
@RunWith(SpringRunner.class)
@DataMongoTest
public class ChangeStreamsTests extends ReplicaSetInitiatingTest {

	@Autowired MessageListenerContainer container; // for imperative style

	@Autowired ReactiveMongoOperations reactiveTemplate; // for reactive style

	Person gabriel = new Person("Gabriel", "Lorca", 30);
	Person michael = new Person("Michael", "Burnham", 30);
	Person ash = new Person("Ash", "Tyler", 35);

	/**
	 * Configuration? Yes we need a bit of it - Do not worry, it won't be much!
	 */
	@Configuration
	@EnableAutoConfiguration
	static class Config {

		/**
		 * Since listening to a <a href="https://docs.mongodb.com/manual/changeStreams/">Change Stream</a> using the sync
		 * MongoDB Java Driver is a blocking class, we need to move load to another {@link Thread} by simply using a
		 * {@link MessageListenerContainer}.
		 * <p />
		 * As this is a {@link org.springframework.context.SmartLifecycle smart lifecycle component} we do actually not need
		 * to worry about its lifecycle, the resource allocation and freeing.
		 *
		 * @param template must not be {@literal null}.
		 * @return a new {@link MessageListenerContainer}
		 */
		@Bean
		MessageListenerContainer messageListenerContainer(MongoTemplate template) {

			return new DefaultMessageListenerContainer(template) {

				/* auto startup will be changed for M2, so this should no longer be required. */
				@Override
				public boolean isAutoStartup() {
					return true;
				}
			};
		}
	}

	/**
	 * Use the {@link MessageListenerContainer} registered within the
	 * {@link org.springframework.context.ApplicationContext} to subscribe to a MongoDB Change Stream. Events published
	 * via {@link com.mongodb.client.ChangeStreamIterable} are passed to the
	 * {@link org.springframework.data.mongodb.core.messaging.MessageListener#onMessage(Message) MessageListener}.
	 */
	@Test
	public void imperativeChangeEvents() throws InterruptedException {

		CollectingMessageListener<ChangeStreamDocument<Document>, Person> messageListener = new CollectingMessageListener();

		ChangeStreamRequest<Person> request = ChangeStreamRequest.builder() //
				.collection("person") //
				.filter(newAggregation(match(where("operationType").is("insert")))) // we are only interested in inserts
				.publishTo(messageListener) //
				.build();

		Subscription subscription = container.register(request, Person.class);
		subscription.await(Duration.ofMillis(200)); // wait till the subscription becomes active

		template.save(gabriel);
		template.save(ash);

		Thread.sleep(200);

		assertThat(messageListener.messageCount()).isEqualTo(2); // first two insert events, so far so good

		template.update(Person.class) //
				.matching(query(where("id").is(ash.getId()))) //
				.apply(update("age", 40)) //
				.first();

		Thread.sleep(200);

		assertThat(messageListener.messageCount()).isEqualTo(2); // updates are skipped

		template.save(michael);

		Thread.sleep(200);

		assertThat(messageListener.messageCount()).isEqualTo(3); // there we go, all events received.
	}

	/**
	 * Use the {@link reactor.core.publisher.Flux} to subscribe to a MongoDB Change Stream.
	 */
	@Test
	public void reactiveChangeEvents() throws InterruptedException {

		BlockingQueue<ChangeStreamEvent<Person>> documents = new LinkedBlockingQueue<>(100);

		Disposable disposable = reactiveTemplate.changeStream(newAggregation(match(where("operationType").is("insert"))),
				Person.class, ChangeStreamOptions.empty(), "person").doOnNext(documents::add).subscribe();

		Thread.sleep(200); // wait till the subscription becomes active

		StepVerifier.create(reactiveTemplate.save(gabriel)).expectNextCount(1).verifyComplete();
		StepVerifier.create(reactiveTemplate.save(ash)).expectNextCount(1).verifyComplete();

		Thread.sleep(200);

		assertThat(documents.size()).isEqualTo(2); // first two insert events, so far so good

		StepVerifier.create(reactiveTemplate.update(Person.class) //
				.matching(query(where("id").is(ash.getId()))) //
				.apply(update("age", 40)) //
				.first()).expectNextCount(1).verifyComplete();

		Thread.sleep(200);

		assertThat(documents.size()).isEqualTo(2); // updates are skipped

		StepVerifier.create(reactiveTemplate.save(michael)).expectNextCount(1).verifyComplete();

		Thread.sleep(200); // just give it some time to link receive all events

		assertThat(documents.size()).isEqualTo(3); // there we go, all events received.

		disposable.dispose();
	}
}
