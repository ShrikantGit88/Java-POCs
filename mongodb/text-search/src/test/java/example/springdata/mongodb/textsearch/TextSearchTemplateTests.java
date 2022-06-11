/*
 * Copyright 2014 the original author or authors.
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
package example.springdata.mongodb.textsearch;

import static example.springdata.mongodb.util.ConsoleResultPrinter.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;

import com.mongodb.MongoClient;

import example.springdata.mongodb.util.BlogPostInitializer;

/**
 * @author Christoph Strobl
 */
public class TextSearchTemplateTests {

	MongoTemplate template;

	@Before
	public void setUp() throws Exception {

		template = new MongoTemplate(new MongoClient(), MongoTestConfiguration.DATABASE_NAME);
		template.dropCollection(BlogPost.class);

		createIndex();
		loadTestData();
	}

	/**
	 * Show how to do simple matching. <br />
	 * Note that text search is case insensitive and will also find entries like {@literal releases}.
	 */
	@Test
	public void findAllBlogPostsWithRelease() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matchingAny("release");
		List<BlogPost> blogPosts = template.find(query(criteria), BlogPost.class);
		printResult(blogPosts, criteria);
	}

	/**
	 * Sort by relevance relying on the value marked with {@link org.springframework.data.mongodb.core.mapping.TextScore}.
	 */
	@Test
	public void findAllBlogPostsByPhraseSortByScore() {

		TextCriteria criteria = TextCriteria.forDefaultLanguage().matchingPhrase("release");

		TextQuery query = new TextQuery(criteria);
		query.setScoreFieldName("score");
		query.sortByScore();

		List<BlogPost> blogPosts = template.find(query, BlogPost.class);
		printResult(blogPosts, criteria);
	}

	/**
	 * Creates the mongodb text index for {@link BlogPost}. <br />
	 * 
	 * <pre>
	 * <code>
	 * db.collection.ensureIndex(
	 * {
	 *     "title" : "text" 
	 *     "content" : "text"
	 *     "categories" : "text",
	 * },
	 * {
	 *     weights : {
	 *         "title" : 3,
	 *         "content" : 2
	 *     }
	 * }
	 * )
	 * </code>
	 * </pre>
	 */
	private void createIndex() {

		TextIndexDefinition textIndex = new TextIndexDefinitionBuilder()//
				.onField("title", 3F) //
				.onField("content", 2F) //
				.onField("categories") //
				.build();

		template.indexOps(BlogPost.class).ensureIndex(textIndex);
	}

	private void loadTestData() throws Exception {

		BlogPostInitializer initializer = new BlogPostInitializer(MongoTestConfiguration.BLOG_POST_ATOM_FEED_SOURCE);
		initializer.initialize(this.template);
	}

}
