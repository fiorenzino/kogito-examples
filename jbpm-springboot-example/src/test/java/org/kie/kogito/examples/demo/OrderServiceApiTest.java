package org.kie.kogito.examples.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.kogito.Model;
import org.kie.kogito.auth.SecurityPolicy;
import org.kie.kogito.examples.DemoApplication;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstances;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.services.identity.StaticIdentityProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = DemoApplication.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD) // reset spring context after each test method
public class OrderServiceApiTest {

    @Autowired
    @Qualifier("demo.orders")
    Process<? extends Model> orderProcess;

    @Autowired
    @Qualifier("demo.orderItems")
    Process<? extends Model> orderItemsProcess;
        
    private SecurityPolicy policy = SecurityPolicy.of(new StaticIdentityProvider("john", Collections.singletonList("managers")));


    @Test
    public void testOrderProcess() {
        assertNotNull(orderProcess);

        Model m = orderProcess.createModel();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("approver",
                       "john");
        parameters.put("order",
                       new Order("12345",
                                 false,
                                 0.0));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = orderProcess.createInstance(m);
        processInstance.start();

        assertEquals(ProcessInstance.STATE_ACTIVE,
                     processInstance.status());
        Model result = (Model) processInstance.variables();
        assertEquals(2,
                     result.toMap().size());
        assertTrue(((Order) result.toMap().get("order")).getTotal() > 0);

        ProcessInstances<? extends Model> orderItemProcesses = orderItemsProcess.instances();
        assertEquals(1,
                     orderItemProcesses.values().size());

        ProcessInstance<?> childProcessInstance = orderItemProcesses.values().iterator().next();

        List<WorkItem> workItems = childProcessInstance.workItems(policy);
        assertEquals(1,
                     workItems.size());

        childProcessInstance.completeWorkItem(workItems.get(0).getId(),
                                              null,
                                              policy);

        assertEquals(ProcessInstance.STATE_COMPLETED,
                     childProcessInstance.status());
        assertEquals(ProcessInstance.STATE_COMPLETED,
                     processInstance.status());

        // no active process instances for both orders and order items processes
        assertEquals(0,
                     orderProcess.instances().values().size());
        assertEquals(0,
                     orderItemsProcess.instances().values().size());
    }
    
    @Test
    public void testOrderProcessWithError() {
        assertNotNull(orderProcess);

        Model m = orderProcess.createModel();
        Map<String, Object> parameters = new HashMap<>();        
        parameters.put("order", new Order("12345", false, 0.0));
        m.fromMap(parameters);

        ProcessInstance<?> processInstance = orderProcess.createInstance(m);
        processInstance.start();

        assertEquals(ProcessInstance.STATE_ERROR, processInstance.status());               
        assertTrue(processInstance.error().isPresent());
        
        parameters = new HashMap<>();
        parameters.put("approver", "john");
        parameters.put("order", new Order("12345", false, 0.0));
        m.fromMap(parameters);
        ((ProcessInstance)processInstance).updateVariables(m);
        
        processInstance.error().get().retrigger();
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.status());
        
        Model result = (Model) processInstance.variables();
        assertEquals(2, result.toMap().size());
        assertTrue(((Order) result.toMap().get("order")).getTotal() > 0);

        ProcessInstances<? extends Model> orderItemProcesses = orderItemsProcess.instances();
        assertEquals(1, orderItemProcesses.values().size());

        ProcessInstance<?> childProcessInstance = orderItemProcesses.values().iterator().next();

        List<WorkItem> workItems = childProcessInstance.workItems(policy);
        assertEquals(1, workItems.size());

        childProcessInstance.completeWorkItem(workItems.get(0).getId(), null, policy);

        assertEquals(ProcessInstance.STATE_COMPLETED, childProcessInstance.status());
        assertEquals(ProcessInstance.STATE_COMPLETED, processInstance.status());

        // no active process instances for both orders and order items processes
        assertEquals(0, orderProcess.instances().values().size());
        assertEquals(0, orderItemsProcess.instances().values().size());
    }
}