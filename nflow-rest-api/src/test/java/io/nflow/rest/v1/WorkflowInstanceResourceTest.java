package io.nflow.rest.v1;

import static com.nitorcreations.Matchers.hasField;
import static io.nflow.engine.workflow.instance.WorkflowInstanceAction.WorkflowActionType.externalChange;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import java.util.Collections;
import java.util.Optional;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import io.nflow.engine.internal.workflow.ObjectStringMapper;
import io.nflow.engine.service.WorkflowInstanceService;
import io.nflow.engine.workflow.instance.QueryWorkflowInstances;
import io.nflow.engine.workflow.instance.WorkflowInstance;
import io.nflow.engine.workflow.instance.WorkflowInstance.WorkflowInstanceStatus;
import io.nflow.engine.workflow.instance.WorkflowInstanceAction;
import io.nflow.engine.workflow.instance.WorkflowInstanceAction.WorkflowActionType;
import io.nflow.engine.workflow.instance.WorkflowInstanceFactory;
import io.nflow.rest.v1.converter.CreateWorkflowConverter;
import io.nflow.rest.v1.converter.ListWorkflowInstanceConverter;
import io.nflow.rest.v1.msg.CreateWorkflowInstanceRequest;
import io.nflow.rest.v1.msg.ListWorkflowInstanceResponse;
import io.nflow.rest.v1.msg.SetSignalRequest;
import io.nflow.rest.v1.msg.UpdateWorkflowInstanceRequest;

@RunWith(MockitoJUnitRunner.class)
public class WorkflowInstanceResourceTest {

  @Mock
  private WorkflowInstanceService workflowInstances;

  @Mock
  private CreateWorkflowConverter createWorkflowConverter;

  @Mock
  private ListWorkflowInstanceConverter listWorkflowConverter;

  @Mock
  private WorkflowInstanceFactory workflowInstanceFactory;

  private WorkflowInstanceResource resource;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Captor
  private ArgumentCaptor<WorkflowInstance> workflowInstanceCaptor;

  @Before
  public void setup() {
    resource = new WorkflowInstanceResource(workflowInstances, createWorkflowConverter, listWorkflowConverter,
        workflowInstanceFactory);
    when(workflowInstanceFactory.newWorkflowInstanceBuilder())
        .thenReturn(new WorkflowInstance.Builder(new ObjectStringMapper(new ObjectMapper())));
  }

  @Test
  public void createWorkflowInstanceWorks() {
    CreateWorkflowInstanceRequest req = new CreateWorkflowInstanceRequest();
    WorkflowInstance inst = mock(WorkflowInstance.class);
    when(createWorkflowConverter.convert(req)).thenReturn(inst);
    when(workflowInstances.insertWorkflowInstance(inst)).thenReturn(1);
    Response r = resource.createWorkflowInstance(req);
    assertThat(r.getStatus(), is(201));
    assertThat(r.getHeaderString("Location"), is("1"));
    verify(createWorkflowConverter).convert(req);
    verify(workflowInstances).insertWorkflowInstance(any(WorkflowInstance.class));
    verify(workflowInstances).getWorkflowInstance(1);
  }

  @Test
  public void whenUpdatingWithoutParametersNothingHappens() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    resource.updateWorkflowInstance(3, req);
    verify(workflowInstances, never()).updateWorkflowInstance(any(WorkflowInstance.class), any(WorkflowInstanceAction.class));
  }

  @Test
  public void whenUpdatingMessageStateTextIsUpdated() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    req.actionDescription = "my desc";
    resource.updateWorkflowInstance(3, req);

    verify(workflowInstances).updateWorkflowInstance(
            (WorkflowInstance) argThat(allOf(hasField("state", equalTo(req.state)), hasField("status", equalTo(null)))),
            (WorkflowInstanceAction) argThat(hasField("stateText", equalTo("my desc"))));
  }

  @Test
  public void whenUpdatingStateUpdateWorkflowInstanceWorks() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    req.state = "newState";
    resource.updateWorkflowInstance(3, req);
    verify(workflowInstances).updateWorkflowInstance(
        (WorkflowInstance) argThat(allOf(hasField("state", equalTo(req.state)), hasField("status", equalTo(null)))),
        (WorkflowInstanceAction) argThat(hasField("stateText", equalTo("API changed state to newState."))));
  }

  @Test
  public void whenUpdatingStateWithDescriptionUpdateWorkflowInstanceWorks() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    req.state = "newState";
    req.actionDescription = "description";
    resource.updateWorkflowInstance(3, req);
    verify(workflowInstances).updateWorkflowInstance(
        (WorkflowInstance) argThat(allOf(hasField("state", equalTo(req.state)), hasField("status", equalTo(null)))),
        (WorkflowInstanceAction) argThat(allOf(hasField("stateText", equalTo("description")), hasField("type", equalTo(externalChange)))));
  }

  @Test
  public void whenUpdatingNextActivationTimeUpdateWorkflowInstanceWorks() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    req.nextActivationTime = new DateTime(2014,11,12,17,55,0);
    resource.updateWorkflowInstance(3, req);
    verify(workflowInstances).updateWorkflowInstance(
        (WorkflowInstance) argThat(allOf(hasField("state", equalTo(null)), hasField("status", equalTo(null)))),
        (WorkflowInstanceAction) argThat(allOf(hasField("stateText", equalTo("API changed nextActivationTime to "
            + req.nextActivationTime + ".")), hasField("type", equalTo(externalChange)))));
  }

  @Test
  public void whenUpdatingNextActivationTimeWithDescriptionUpdateWorkflowInstanceWorks() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    req.nextActivationTime = new DateTime(2014, 11, 12, 17, 55, 0);
    req.actionDescription = "description";
    resource.updateWorkflowInstance(3, req);
    verify(workflowInstances).updateWorkflowInstance(
        (WorkflowInstance) argThat(allOf(hasField("state", equalTo(null)), hasField("status", equalTo(null)))),
        (WorkflowInstanceAction) argThat(allOf(hasField("stateText", equalTo("description")), hasField("type", equalTo(externalChange)))));
  }

  @Test
  public void whenUpdatingStateVariablesUpdateWorkflowInstanceWorks() {
    UpdateWorkflowInstanceRequest req = new UpdateWorkflowInstanceRequest();
    req.stateVariables.put("foo", "bar");
    req.stateVariables.put("textNode", new TextNode("text"));
    resource.updateWorkflowInstance(3, req);
    verify(workflowInstances).updateWorkflowInstance(workflowInstanceCaptor.capture(),
        (WorkflowInstanceAction) argThat(hasField("stateText", equalTo("API updated state variables."))));
    WorkflowInstance instance = workflowInstanceCaptor.getValue();
    assertThat(instance.getStateVariable("foo"), is("bar"));
    assertThat(instance.getStateVariable("textNode"), is("\"text\""));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void listWorkflowInstancesWorks() {
    resource.listWorkflowInstances(asList(42), asList("type"), 99, 88, asList("state"),
        asList(WorkflowInstanceStatus.created), "businessKey", "externalId", "", null, null);
    verify(workflowInstances).listWorkflowInstances((QueryWorkflowInstances) argThat(allOf(
      hasField("ids", contains(42)),
      hasField("types", contains("type")),
      hasField("parentWorkflowId", is(99)),
      hasField("parentActionId", is(88)),
      hasField("states", contains("state")),
      hasField("statuses", contains(WorkflowInstanceStatus.created)),
      hasField("businessKey", equalTo("businessKey")),
      hasField("externalId", equalTo("externalId")),
      hasField("includeActions", equalTo(false)),
      hasField("includeCurrentStateVariables", equalTo(false)),
      hasField("includeActionStateVariables", equalTo(false)),
      hasField("includeChildWorkflows", equalTo(false)),
      hasField("maxResults", equalTo(null)),
      hasField("maxActions", equalTo(null)))));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void listWorkflowInstancesWorksWithAllIncludes() {
    resource.listWorkflowInstances(asList(42), asList("type"), 99, 88, asList("state"),
        asList(WorkflowInstanceStatus.created, WorkflowInstanceStatus.executing),
        "businessKey", "externalId", "actions,currentStateVariables,actionStateVariables,childWorkflows", 1L, 1L);
    verify(workflowInstances).listWorkflowInstances((QueryWorkflowInstances) argThat(allOf(
      hasField("ids", contains(42)),
      hasField("types", contains("type")),
      hasField("parentWorkflowId", is(99)),
      hasField("parentActionId", is(88)),
      hasField("states", contains("state")),
      hasField("statuses", contains(WorkflowInstanceStatus.created, WorkflowInstanceStatus.executing)),
      hasField("businessKey", equalTo("businessKey")),
      hasField("externalId", equalTo("externalId")),
      hasField("includeActions", equalTo(true)),
      hasField("includeCurrentStateVariables", equalTo(true)),
      hasField("includeActionStateVariables", equalTo(true)),
      hasField("includeChildWorkflows", equalTo(true)),
      hasField("maxResults", equalTo(1L)),
      hasField("maxActions", equalTo(1L)))));
  }

  @Test
  public void fetchingNonExistingWorkflowThrowsNotFoundException() {
    thrown.expect(NotFoundException.class);
    when(workflowInstances.listWorkflowInstances(any(QueryWorkflowInstances.class))).thenReturn(
        Collections.<WorkflowInstance> emptyList());
    resource.fetchWorkflowInstance(42, null, null);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void fetchingExistingWorkflowReturnFirstWorkflowInstance() {
    WorkflowInstance instance1 = mock(WorkflowInstance.class);
    WorkflowInstance instance2 = mock(WorkflowInstance.class);
    QueryWorkflowInstances query = (QueryWorkflowInstances) argThat(allOf(
        hasField("ids", contains(42)),
        hasField("types", emptyCollectionOf(String.class)),
        hasField("states", emptyCollectionOf(String.class)),
        hasField("businessKey", equalTo(null)),
        hasField("externalId", equalTo(null)),
        hasField("includeActions", equalTo(false)),
        hasField("includeCurrentStateVariables", equalTo(false)),
        hasField("includeActionStateVariables", equalTo(false)),
        hasField("includeChildWorkflows", equalTo(false)),
        hasField("maxResults", equalTo(1L)),
        hasField("maxActions", equalTo(null))));
    when(workflowInstances.listWorkflowInstances(query)).thenReturn(asList(instance1, instance2));
    ListWorkflowInstanceResponse resp1 = mock(ListWorkflowInstanceResponse.class);
    ListWorkflowInstanceResponse resp2 = mock(ListWorkflowInstanceResponse.class);
    when(listWorkflowConverter.convert(eq(instance1), any(QueryWorkflowInstances.class))).thenReturn(resp1, resp2);
    ListWorkflowInstanceResponse result = resource.fetchWorkflowInstance(42, null, null);
    verify(workflowInstances).listWorkflowInstances(any(QueryWorkflowInstances.class));
    assertEquals(resp1, result);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void fetchingExistingWorkflowWorksWithAllIncludes() {
    WorkflowInstance instance1 = mock(WorkflowInstance.class);
    QueryWorkflowInstances query = (QueryWorkflowInstances) argThat(allOf(
        hasField("ids", contains(42)),
        hasField("types", emptyCollectionOf(String.class)),
        hasField("states", emptyCollectionOf(String.class)),
        hasField("businessKey", equalTo(null)),
        hasField("externalId", equalTo(null)),
        hasField("includeActions", equalTo(true)),
        hasField("includeCurrentStateVariables", equalTo(true)),
        hasField("includeActionStateVariables", equalTo(true)),
        hasField("includeChildWorkflows", equalTo(true)),
        hasField("maxResults", equalTo(1L)),
        hasField("maxActions", equalTo(1L))));
    when(workflowInstances.listWorkflowInstances(query)).thenReturn(asList(instance1));
    ListWorkflowInstanceResponse resp1 = mock(ListWorkflowInstanceResponse.class);
    when(listWorkflowConverter.convert(eq(instance1), any(QueryWorkflowInstances.class))).thenReturn(resp1);
    ListWorkflowInstanceResponse result = resource.fetchWorkflowInstance(42,
        "actions,currentStateVariables,actionStateVariables,childWorkflows", 1L);
    verify(workflowInstances).listWorkflowInstances(any(QueryWorkflowInstances.class));
    assertEquals(resp1, result);
  }

  @Test
  public void setSignalWorks() {
    SetSignalRequest req = new SetSignalRequest();
    req.signal = 42;
    req.reason = "testing";
    when(workflowInstances.setSignal(99, Optional.of(42), "testing", WorkflowActionType.externalChange)).thenReturn(true);

    Response response = resource.setSignal(99, req);

    verify(workflowInstances).setSignal(99, Optional.of(42), "testing", WorkflowActionType.externalChange);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(response.readEntity(String.class), is("Signal was set successfully"));
  }

  @Test
  public void setSignalReturnsOkWhenSignalIsNotUpdated() {
    SetSignalRequest req = new SetSignalRequest();
    req.signal = null;
    req.reason = "testing";
    when(workflowInstances.setSignal(99, Optional.empty(), "testing", WorkflowActionType.externalChange)).thenReturn(false);

    Response response = resource.setSignal(99, req);

    verify(workflowInstances).setSignal(99, Optional.empty(), "testing", WorkflowActionType.externalChange);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(response.readEntity(String.class), is("Signal was not set"));
  }

}
