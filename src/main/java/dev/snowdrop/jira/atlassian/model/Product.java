/**
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package dev.snowdrop.jira.atlassian.model;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class Product implements IssueSource {
	private final Component component;

	public Product(Component component) {
		this.component = component;
	}

	@Override
	public Release getParent() {
		return component.getParent();
	}

	@Override
	public String getName() {
		return component.getName();
	}

	@Override
	public String getTitle() {
		return component.getTitle();
	}

	@Override
	public String getJira() {
		return component.getProductAsString();
	}

	@Override
	public String getDescription() {
		return null;
	}
}