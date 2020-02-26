package plugins;

import annexes.message.interfaces.MessageI;
import connectors.PublicationConnector;
import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.reflection.connectors.ReflectionConnector;
import fr.sorbonne_u.components.reflection.interfaces.ReflectionI;
import fr.sorbonne_u.components.reflection.ports.ReflectionOutboundPort;
import interfaces.PublicationCI;
import interfaces.PublicationsImplementationI;
import launcher.CVM;
import ports.PublicationCOutBoundPort;

public class PublisherPlugin 
extends AbstractPlugin implements PublicationsImplementationI{

	private static final long serialVersionUID = 1L;
	protected PublicationCOutBoundPort publicationOutboundPort;
	

	/**-----------------------------------------------------
	 * ------------------ CYCLE OF LIFE --------------------
	 ------------------------------------------------------*/	
	
	@Override
	public void installOn(ComponentI owner) throws Exception {
		super.installOn(owner) ;

		// Add interfaces and create ports
		this.addRequiredInterface(PublicationCI.class) ;
		this.publicationOutboundPort = new PublicationCOutBoundPort(this.owner) ;
		this.publicationOutboundPort.publishPort() ;
	}

	
	@Override
	public void initialise() throws Exception {
		// Use the reflection approach to get the URI of the inbound port
		// of the hash map component.
		this.addRequiredInterface(ReflectionI.class) ;
		ReflectionOutboundPort rop = new ReflectionOutboundPort(this.owner) ;
		rop.publishPort() ;
		this.owner.doPortConnection(
				rop.getPortURI(),
				CVM.URIBrokerPublicationInboundPortURI,
				ReflectionConnector.class.getCanonicalName()) ;
		String[] uris = rop.findPortURIsFromInterface(PublicationCI.class) ;
		
		assert	uris != null && uris.length == 1 ;

		this.owner.doPortDisconnection(rop.getPortURI()) ;
		rop.unpublishPort() ;
		rop.destroyPort() ;
		this.removeRequiredInterface(ReflectionI.class) ;

		// connect the outbound port.
		this.owner.doPortConnection(
				this.publicationOutboundPort.getPortURI(),
				uris[0],
				PublicationConnector.class.getCanonicalName()) ;

		super.initialise();
	}
	
	
	@Override
	public void finalise() throws Exception {
		this.owner.doPortDisconnection(this.publicationOutboundPort.getPortURI()) ;
	}

	
	@Override
	public void	uninstall() throws Exception {
		this.publicationOutboundPort.unpublishPort() ;
		this.publicationOutboundPort.destroyPort() ;
		this.removeRequiredInterface(PublicationCI.class) ;
	}
	
	
	
	/**------------------------------------------------------------------
	 * ---------------- PLUGIN SERVICES IMPLEMENTATION ------------------
	 ------------------------------------------------------------------*/
	@Override
	public void publish(MessageI m, String topic) throws Exception {
		this.publicationOutboundPort.publish(m, topic);
	}

	@Override
	public void publish(MessageI m, String[] topics) throws Exception {
		this.publicationOutboundPort.publish(m, topics);
	}

	@Override
	public void publish(MessageI[] ms, String topic) throws Exception {
		this.publicationOutboundPort.publish(ms, topic);
	}

	@Override
	public void publish(MessageI[] ms, String[] topics) throws Exception {
		this.publicationOutboundPort.publish(ms, topics);
	}
	
}