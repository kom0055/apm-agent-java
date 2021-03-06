/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments servlets to create transactions.
 * <p>
 * If the transaction has already been recorded with the help of {@link FilterChainInstrumentation},
 * it does not record the transaction again.
 * But if there is no filter registered for a servlet,
 * this makes sure to record a transaction in that case.
 * </p>
 */
public class ServletInstrumentation extends AbstractServletInstrumentation {

    static final String SERVLET_API = "servlet-api";

    @Nullable
    @VisibleForAdvice
    // referring to HttpServletRequest is legal because of type erasure
    public static HelperClassManager<ServletTransactionCreationHelper<HttpServletRequest>> servletTransactionCreationHelperManager;

    public ServletInstrumentation(ElasticApmTracer tracer) {
        ServletApiAdvice.init(tracer);
        // adding a null-check before setting helper manager reference breaks test execution, which prevents having
        // the same code construct we have for other HelperClassManager usages.
        //
        // This should probably be changed when upgrading this plugin to use HelperClassManager for all helper
        // classes.
        servletTransactionCreationHelperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
            "co.elastic.apm.agent.servlet.helper.ServletTransactionCreationHelperImpl",
            "co.elastic.apm.agent.servlet.helper.ServletRequestHeaderGetter"
        );
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("javax.servlet.Servlet")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("service")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return ServletApiAdvice.class;
    }

    @VisibleForAdvice
    public interface ServletTransactionCreationHelper<R> {
        @Nullable
        Transaction createAndActivateTransaction(R request);
    }
}
