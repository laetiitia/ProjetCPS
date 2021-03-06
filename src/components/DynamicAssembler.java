package components;

import java.util.HashSet;
import java.util.Set;

import components.Broker;
import components.Publisher;
import components.Subscriber;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.pre.dcc.connectors.DynamicComponentCreationConnector;
import fr.sorbonne_u.components.pre.dcc.interfaces.DynamicComponentCreationI;
import fr.sorbonne_u.components.pre.dcc.ports.DynamicComponentCreationOutboundPort;
import launcher.CVMDynamicCreationLife;


/**
 * The class <code> DynamicAssembler </code> represent 
 * the assembler that will create all the component
 * actively
 * 
 * 
 * @author GROUP LAMA
 *
 */
@RequiredInterfaces(required = {DynamicComponentCreationI.class})
public class DynamicAssembler 
extends AbstractComponent {
	
	protected DynamicComponentCreationOutboundPort	dynamicOutBoundPort;
	protected String								jvmURI ;
	protected Set<String>							deployerURIs ;
	
	/**
	 * Constructor of DynamicAssembler
	 * 
	 * @pre URI != null;
	 * 
	 * @param URI of this component
	 * @param nbThreads number of threads
	 * @param nbSchedulableThreads number of schedular threads
	 * @throws Exception
	 */
	protected DynamicAssembler(String URI, int nbThreads, int nbSchedulableThreads) throws Exception{
		super(nbThreads, nbSchedulableThreads);
		assert URI != null;
		this.jvmURI=URI;
		
		this.tracer.setTitle("DynamicAssembler") ;
		this.tracer.setRelativePosition(2, 2) ;
		this.toggleTracing() ;
	}
	
	@Override
	public void	start() throws ComponentStartException {
		super.start() ;
		this.logMessage("starting DynamicAssembler component.") ;
		
		//Connecte ce composant au composant DynamicComponentCreator
		try {
			
			this.dynamicOutBoundPort = new DynamicComponentCreationOutboundPort(this) ;
			this.dynamicOutBoundPort.publishPort() ;
			this.doPortConnection(
				this.dynamicOutBoundPort.getPortURI(),
				this.jvmURI+ AbstractCVM.DCC_INBOUNDPORT_URI_SUFFIX,
				DynamicComponentCreationConnector.class.getCanonicalName()) ;
		} catch (Exception e) {
			throw new ComponentStartException(e) ;
		}
		
	}
	
	@Override
	public void	execute() throws Exception{
		super.execute();
		
		//On crée tous les composants dont on a besoin
		int nbThread=1;
		int nbSchedulableThreads=0;
		this.deployerURIs = new HashSet<String>();
		
		/**-----------------
		 * Broker
		 -------------------*/
		String brokerURI =
				this.dynamicOutBoundPort.createComponent(
					Broker.class.getCanonicalName(),
					new Object[] {
							nbThread,
							nbSchedulableThreads,
							CVMDynamicCreationLife.BROKER_COMPONENT_URI}) ;
		this.deployerURIs.add(brokerURI) ;

		
		/**-----------------
		 * Publisher
		 ------------------*/
		String publisherURI =
				this.dynamicOutBoundPort.createComponent(
					Publisher.class.getCanonicalName(),
					new Object[] {
							nbThread,
							nbSchedulableThreads,
							CVMDynamicCreationLife.PUBLISHER_COMPONENT_URI,
							CVMDynamicCreationLife.BROKER_COMPONENT_URI}) ;
		this.deployerURIs.add(publisherURI) ;
		
		/**---------------
		 * Subscriber
		 ----------------*/
		String subscriberURI =
				this.dynamicOutBoundPort.createComponent(
					Subscriber.class.getCanonicalName(),
					new Object[] {
							nbThread,
							nbSchedulableThreads,
							CVMDynamicCreationLife.SUBSCRIBE_COMPONENT_URI,
							CVMDynamicCreationLife.BROKER_COMPONENT_URI}) ;
		this.deployerURIs.add(subscriberURI) ;
		
		
		for (String uri : this.deployerURIs) {
			this.dynamicOutBoundPort.startComponent(uri) ;
		}
		this.logMessage("Starting Components");
		
		for (String uri : this.deployerURIs) {
			this.dynamicOutBoundPort.executeComponent(uri) ;
		}
		this.logMessage("Executing Components");
		
	}
	
	@Override
	public void	finalise() throws Exception{
		this.logMessage("stopping publisher component.") ;
		this.printExecutionLogOnFile("publisher");
		this.doPortDisconnection(this.dynamicOutBoundPort.getPortURI()) ;
		super.finalise();
	}
	
	@Override
	public void shutdown() throws ComponentShutdownException {
		try {
			this.dynamicOutBoundPort.unpublishPort() ;
		} catch (Exception e) {
			throw new ComponentShutdownException(e) ;
		}
		super.shutdown();
	}

}
