/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.test.integration.wildfly;

import java.util.List;

import javax.inject.Inject;

import org.hibernate.search.test.integration.VersionTestHelper;
import org.hibernate.search.test.integration.wildfly.controller.MemberRegistration;
import org.hibernate.search.test.integration.wildfly.model.Member;
import org.hibernate.search.test.integration.wildfly.util.Resources;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.persistence20.PersistenceDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test the Hibernate Search module in Wildfly combined with an Infinispan Directory usage.
 *
 * @author Sanne Grinovero
 */
@RunWith(Arquillian.class)
public class InfinispanModuleMemberRegistrationIT {

	@Deployment(name = "dep.active-1")
	@TargetsContainer("container.active-1")
	public static Archive<?> createTestDeploymentOne() {
		return createTestArchive();
	}

	@Deployment(name = "dep.active-2")
	@TargetsContainer("container.active-2")
	public static Archive<?> createTestDeploymentTwo() {
		return createTestArchive();
	}

	private static Archive<?> createTestArchive() {
		return ShrinkWrap
				.create( WebArchive.class, InfinispanModuleMemberRegistrationIT.class.getSimpleName() + ".war" )
				.addClasses( Member.class, MemberRegistration.class, Resources.class )
				.addAsResource( persistenceXml(), "META-INF/persistence.xml" )
				.add( VersionTestHelper.moduleDependencyManifest(), "META-INF/MANIFEST.MF" )
				//This test is simply reusing the default configuration file, but we copy
				//this configuration into the Archive to verify that resources can be loaded from it:
				.addAsResource( "user-provided-infinispan.xml", "user-provided-infinispan.xml" )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" );
	}

	private static Asset persistenceXml() {
		String persistenceXml = Descriptors.create( PersistenceDescriptor.class )
				.version( "2.0" )
				.createPersistenceUnit()
				.name( "primary" )
				.jtaDataSource( "java:jboss/datasources/ExampleDS" )
				.getOrCreateProperties()
				.createProperty()
				.name( "hibernate.hbm2ddl.auto" )
				.value( "create-drop" )
				.up()
				.createProperty()
				.name( "hibernate.search.default.lucene_version" )
				.value( "LUCENE_CURRENT" )
				.up()
				.createProperty()
				.name( "hibernate.search.default.directory_provider" )
				.value( "infinispan" )
				.up()
				.createProperty()
				.name( "hibernate.search.default.exclusive_index_use" )
				.value( "false" )
				.up()
				.createProperty()
				.name( "hibernate.search.infinispan.configuration_resourcename" )
				.value( "user-provided-infinispan.xml" )
				.up()
				.up()
				.up()
				.exportAsString();
		return new StringAsset( persistenceXml );
	}

	@Inject
	MemberRegistration memberRegistration;

	@Test @InSequence(value = 1) @OperateOnDeployment("dep.active-1")
	public void testRegister() throws Exception {
		Member newMember = memberRegistration.getNewMember();
		newMember.setName( "Davide D'Alto" );
		newMember.setEmail( "davide@mailinator.com" );
		newMember.setPhoneNumber( "2125551234" );
		memberRegistration.register();

		assertNotNull( newMember.getId() );
	}

	@Test @InSequence(value = 2) @OperateOnDeployment("dep.active-2")
	public void testNewMemberSearch() throws Exception {
		Member newMember = memberRegistration.getNewMember();
		newMember.setName( "Peter O'Tall" );
		newMember.setEmail( "peter@mailinator.com" );
		newMember.setPhoneNumber( "4643646643" );
		memberRegistration.register();

		List<Member> search = memberRegistration.search( "Peter" );

		assertFalse( "Expected at least one result after the indexing", search.isEmpty() );
		assertEquals( "Search hasn't found a new member", newMember.getName(), search.get( 0 ).getName() );
	}

	@Test @InSequence(value = 3) @OperateOnDeployment("dep.active-2")
	public void testNonExistingMember() throws Exception {
		List<Member> search = memberRegistration.search( "TotallyInventedName" );

		assertNotNull( "Search should never return null", search );
		assertTrue( "Search results should be empty", search.isEmpty() );
	}
}
