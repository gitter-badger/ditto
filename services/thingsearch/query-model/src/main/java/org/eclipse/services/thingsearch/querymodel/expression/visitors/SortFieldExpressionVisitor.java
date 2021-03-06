/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.services.thingsearch.querymodel.expression.visitors;

import org.eclipse.services.thingsearch.querymodel.expression.PolicyRestrictedFieldExpression;

/**
 * Compositional interpreter of
 * {@link PolicyRestrictedFieldExpression}.
 */
public interface SortFieldExpressionVisitor<T> {

    T visitAttribute(final String key);

    T visitFeatureIdProperty(final String featureId, final String property);

    T visitSimple(final String fieldName);
}
