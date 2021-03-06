/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client.mock;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.mock.crd.DoneablePodSet;
import io.fabric8.kubernetes.client.mock.crd.PodSet;
import io.fabric8.kubernetes.client.mock.crd.PodSetList;
import io.fabric8.kubernetes.client.mock.crd.PodSetSpec;
import io.fabric8.kubernetes.client.mock.crd.PodSetStatus;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.net.HttpURLConnection;
import java.util.Collections;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableRuleMigrationSupport
class TypedCustomResourceApiTest {
  @Rule
  public KubernetesServer server = new KubernetesServer();

  private MixedOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>> podSetClient;

  private CustomResourceDefinition podSetCrd;
  private CustomResourceDefinitionContext crdContext;

  @BeforeEach
  void setupCrd() {
    podSetCrd = new CustomResourceDefinitionBuilder()
      .withNewMetadata().withName("podsets.demo.k8s.io").endMetadata()
      .withNewSpec()
      .withGroup("demo.k8s.io")
      .withVersion("v1alpha1")
      .withNewNames().withKind("PodSet").withPlural("podsets").endNames()
      .withScope("Namespaced")
      .endSpec()
      .build();

    crdContext = CustomResourceDefinitionContext.fromCrd(podSetCrd);
  }

  @Test
  void create() {
    server.expect().post().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets").andReturn(200, getPodSet()).once();

    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);

    PodSet returnedPodSet = podSetClient.inNamespace("test").create(getPodSet());
    assertNotNull(returnedPodSet);
    assertEquals("example-podset", returnedPodSet.getMetadata().getName());
  }

  @Test
  void list() {
    PodSetList podSetList = new PodSetList();
    podSetList.setItems(Collections.singletonList(getPodSet()));
    server.expect().get().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets").andReturn(200, podSetList).once();
    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);

    podSetList = podSetClient.inNamespace("test").list();
    assertNotNull(podSetList);
    assertEquals(1, podSetList.getItems().size());
    assertEquals("example-podset", podSetList.getItems().get(0).getMetadata().getName());
  }

  @Test
  void createOrReplace() {
    server.expect().get().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets/example-podset").andReturn(HttpURLConnection.HTTP_OK, getPodSet()).times(2);
    server.expect().post().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets").andReturn(HttpURLConnection.HTTP_CONFLICT, getPodSet()).times(2);
    server.expect().put().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets/example-podset").andReturn(HttpURLConnection.HTTP_OK, getPodSet()).once();

    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);
    PodSet returnedPodSet = podSetClient.inNamespace("test").createOrReplace(getPodSet());

    assertNotNull(returnedPodSet);
    assertEquals("example-podset", returnedPodSet.getMetadata().getName());
  }

  @Test
  void delete() {
    server.expect().delete().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets/example-podset").andReturn(200, getPodSet()).once();

    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);

    boolean isDeleted = podSetClient.inNamespace("test").withName("example-podset").delete();
    assertTrue(isDeleted);
  }

  @Test
  void testCascadingDeletion() throws InterruptedException {
    server.expect().delete().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets/example-podset").andReturn(200, getPodSet()).once();

    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);

    boolean isDeleted = podSetClient.inNamespace("test").withName("example-podset").cascading(true).delete();
    assertTrue(isDeleted);

    RecordedRequest recordedRequest = server.getLastRequest();
    assertEquals("DELETE", recordedRequest.getMethod());
    assertEquals("{\"apiVersion\":\"v1\",\"kind\":\"DeleteOptions\",\"orphanDependents\":false}", recordedRequest.getBody().readUtf8());
  }

  @Test
  void testPropagationPolicyDeletion() throws InterruptedException {
    server.expect().delete().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets/example-podset").andReturn(200, getPodSet()).once();

    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);

    boolean isDeleted = podSetClient.inNamespace("test").withName("example-podset").withPropagationPolicy(DeletionPropagation.ORPHAN).delete();
    assertTrue(isDeleted);

    RecordedRequest recordedRequest = server.getLastRequest();
    assertEquals("DELETE", recordedRequest.getMethod());
    assertEquals("{\"apiVersion\":\"v1\",\"kind\":\"DeleteOptions\",\"propagationPolicy\":\"Orphan\"}", recordedRequest.getBody().readUtf8());
  }

  @Test
  void testStatusUpdation() throws InterruptedException {
    PodSet updatedPodSet = getPodSet();
    PodSetStatus podSetStatus = new PodSetStatus();
    podSetStatus.setAvailableReplicas(4);
    updatedPodSet.setStatus(podSetStatus);

    server.expect().put().withPath("/apis/demo.k8s.io/v1alpha1/namespaces/test/podsets/example-podset/status").andReturn(200, updatedPodSet).once();
    podSetClient = server.getClient().customResources(crdContext, PodSet.class, PodSetList.class, DoneablePodSet.class);

    podSetClient.inNamespace("test").updateStatus(updatedPodSet);
    RecordedRequest recordedRequest = server.getLastRequest();
    assertEquals("PUT", recordedRequest.getMethod());
    assertEquals("{\"kind\":\"PodSet\",\"apiVersion\":\"demo.k8s.io/v1alpha1\",\"metadata\":{\"name\":\"example-podset\"},\"spec\":{\"replicas\":5},\"status\":{\"availableReplicas\":4}}", recordedRequest.getBody().readUtf8());
    System.out.println(recordedRequest.getBody().readUtf8());
  }

  private PodSet getPodSet() {
    PodSetSpec podSetSpec = new PodSetSpec();
    podSetSpec.setReplicas(5);

    PodSet podSet = new PodSet();
    podSet.setApiVersion("demo.k8s.io/v1alpha1");
    podSet.setMetadata(new ObjectMetaBuilder().withName("example-podset").build());
    podSet.setSpec(podSetSpec);
    return podSet;
  }
}
