package components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import interfaces.ManagementCI;
import interfaces.ReceptionCI;
import interfaces.ReceptionImplementationI;
import ports.ManagementCOutBoundPort;
import ports.ReceptionCInBoundPort;
import annexes.Chrono;
import annexes.message.Properties;
import annexes.message.interfaces.MessageFilterI;
import annexes.message.interfaces.MessageI;

/**
 * The class <code> Subscriber_Distributed </code> represent 
 * a subscriber in a Multi-JVM that will subscribe in specific 
 * topics and that will receive a message if there
 * is a publication in theses topics.
 * 
 * @author GROUP LAMA
 *
 */

@RequiredInterfaces(required = {ManagementCI.class})
@OfferedInterfaces(offered = {ReceptionCI.class} )
public class Subscriber_Distributed 
extends AbstractComponent implements ReceptionImplementationI{
	
	
	/**-------------------- Variables ---------------------*/
	protected final String                       uri;
	private ManagementCOutBoundPort portOutManagement;
	private ReceptionCInBoundPort portInReception;
	private final String receptionInboundPortURI;
	
	
	protected Subscriber_Distributed(int nbThreads, int nbSchedulableThreads, String uri, String URI_BROKER) throws Exception{
		super(uri, nbThreads, nbSchedulableThreads);
		
		assert uri != null;
		assert URI_BROKER != null;
		
		this.uri = uri;
		
		/**----------------- PORTS --------------------**/
		portInReception = new ReceptionCInBoundPort(this);
		portOutManagement = new ManagementCOutBoundPort(this);
		this.addPort(portInReception);
		this.addPort(portOutManagement);
		portInReception.publishPort();
		portOutManagement.publishPort();
		
		this.receptionInboundPortURI = portInReception.getPortURI();
		
		/**----------------- TRACE --------------------**/
		this.tracer.setTitle(uri) ;
		this.tracer.setRelativePosition(0, 1) ;
		this.toggleTracing() ;
	}
	
	/**-----------------------------------------------------
	 * ------------------ CYCLE OF LIFE --------------------
	 ------------------------------------------------------*/
	@Override
	public void	start() throws ComponentStartException {
		super.start() ;
		this.logMessage("starting Subscriber component.") ;
		
		assert	this.isStarted();

	
		/**------------------- Scenarios in order to tests of the methods ---------------**/
		try {	
			String [] topics = {"Automobile", "Voyage","Sport","Nature&Decouvre"};   //Pour que le Subscriber soit abonné aux topics avant la publication
			
			/**
			 * Choose scenario that you want (1 to 4):
			 */
			int[] scenario = {1, 2, 3, 4};
			
			Chrono chrono=new Chrono(); //Chrono permet la preuve que les thread améliore le temps de calcul
			chrono.start();
			
			for(int i=0; i<scenario.length; i++) {
				switch (scenario[i]) {
					case 1: /** Scenario 1: Subscribe to the topic "Peche&Cuisine". We use the filter "thon" **/
						this.logMessage("Subscriber subcribe to the topic Peche&Cuisine.");
						MessageFilterI filter = m -> {Properties props = m.getProperties(); 
													try {
														if(!props.getBooleanProp("thon")) //Don't want a message with the word "thon" inside
															return true;
														return false;
													} catch (Exception e) { e.printStackTrace(); return false; }} ;
						portOutManagement.subscribe("Peche&Cuisine", filter ,receptionInboundPortURI);
						break;
						
					case 2: /** Scenario 2: Subscribe to a list of topics**/
						portOutManagement.subscribe(topics, receptionInboundPortURI);
						this.logMessage("Subscriber subcribe to Automobile");
						break;
						
					case 3: /** Scenario 3: Subscribe to 100 topics**/
						for(int y=0; y<100; y++) {
							portOutManagement.subscribe("topic"+y, receptionInboundPortURI);
						}
						break;
					case 4: /** Le composant subsciber1 s'abonne à Test **/
                        if (this.uri.equals("my-URI-subscribe1")) {
                            portOutManagement.subscribe("Test", receptionInboundPortURI);  
                        }
                        break;
						
					default: break;	
				}
			}
			
			chrono.stop();
			this.logMessage("Chrono sub : "+chrono.getDureeMs()); 
		} catch (Exception e) {
			throw new RuntimeException(e) ;
		}
		/**------------------------------ End of Scenarios -----------------------------**/
	}
	
	
	@Override
	public void	execute() throws Exception{
		super.execute();
	}
	
	
	@Override
	public void	finalise() throws Exception{
		this.logMessage("stopping subscriber component.") ;
		this.printExecutionLogOnFile("subscriber");
		
		this.portOutManagement.doDisconnection();
		this.portOutManagement.unpublishPort();
		
		this.portInReception.doDisconnection();
		this.portInReception.unpublishPort();

		super.finalise();
	}
	

	/**=====================================================================================
	 * =================================== RECEPTIONCI =====================================
	 ======================================================================================*/
	/**
	 * @param m the message to accept 
	 * @throws Exeption
	 */
	@Override
	public void acceptMessage(MessageI m) throws Exception {
		this.logMessage(this.uri+" a reçu : "+ m.getPayload());
		
	}

	/**
	 * @param ms a list of messages to accept
	 * @throws Exeption
	 */
	@Override
	public void acceptMessages(MessageI[] ms) throws Exception {
		for(MessageI m : ms) {
			this.acceptMessage(m);
		}
	}

}