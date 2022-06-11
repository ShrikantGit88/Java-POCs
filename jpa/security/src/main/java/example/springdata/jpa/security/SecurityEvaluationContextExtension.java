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
package example.springdata.jpa.security;

import org.springframework.data.repository.query.spi.EvaluationContextExtension;
import org.springframework.data.repository.query.spi.EvaluationContextExtensionSupport;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link EvaluationContextExtension} to expose Spring Security's principal via a SpEL property.
 *
 * Note that this is just for the sake of demonstration - Spring Security will eventually provide its
 * own EvaluationContext extension.
 * 
 * @author Oliver Gierke
 */
class SecurityEvaluationContextExtension extends EvaluationContextExtensionSupport {

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.query.spi.EvaluationContextExtension#getExtensionId()
	 */
	@Override
	public String getExtensionId() {
		return "security";
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.query.spi.EvaluationContextExtension#getRootObject()
	 */
	@Override
	public SecurityExpressionRoot getRootObject() {
		return new SecurityExpressionRoot(SecurityContextHolder.getContext().getAuthentication()) {};
	}
}
