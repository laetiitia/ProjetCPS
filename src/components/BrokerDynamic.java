package components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import annexes.Client;
import annexes.message.interfaces.MessageFilterI;
import annexes.message.interfaces.MessageI;
import connectors.ReceptionConnector;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.pre.dcc.DynamicComponentCreator;
import interfaces.ManagementCI;
import interfaces.ManagementImplementationI;
import interfaces.PublicationCI;
import interfaces.PublicationsImplementationI;
import interfaces.ReceptionCI;
import interfaces.SubscriptionImplementationI;
import ports.ManagementCInBoundPort;
import ports.PublicationCInBoundPort;
import ports.ReceptionCOutBoundPort;

public class BrokerDynamic 
extends DynamicComponentCreator
implements ManagementImplementationI, SubscriptionImplementationI, PublicationsImplementationI{


	/**------------------- PORTS -------------------------*/
	protected ManagementCInBoundPort      mipPublisher;   // Connected to URIPublisher 
	protected ManagementCInBoundPort      mipSubscriber;  // Connected to URISubscriber 
	protected PublicationCInBoundPort     publicationInboundPort;
	
	
	/**------------------ VARIABLES ----------------------*/
	protected final String                managInboundPortPublisher;
	protected final String                managInboundPortSubscriber;
	protected Map <String, ArrayList<MessageI> >                  topics;          //<Topics, List of messages>
	protected ArrayList<Client>                                   subscribers;     // List of Subscriber
	protected Map <String, ArrayList<Client> >                    subscriptions;   //<Topics, List of Subscriber>
	private int cpt;
	private int indexWrite, indexRead;
	
	/**----------------------- MUTEX ----------------------*/
	protected ReadWriteLock lock = new ReentrantReadWriteLock();
	protected Lock readLock = lock.readLock();
	protected Lock writeLock = lock.writeLock();
	
	
	protected BrokerDynamic(String dynamicComponentCreationInboundPortURI,
							String uri, 
							String managInboundPortPublisher,
							String managInboundPortSubscriber, 
							String publicationInboundPortURI) throws Exception {

		super(dynamicComponentCreationInboundPortURI);
		
		assert managInboundPortPublisher != null;
		assert managInboundPortSubscriber != null;
		assert publicationInboundPortURI != null;
		
		this.managInboundPortPublisher = managInboundPortPublisher;
		this.managInboundPortSubscriber = managInboundPortSubscriber;
		cpt=0;
		
		topics = new HashMap <String, ArrayList<MessageI>>();  
		subscriptions = new HashMap <String, ArrayList<Client>>();  
		subscribers = new ArrayList<Client>();
		
		
		/**----------------- ADD COMPONENTS -------------------*/
		this.addOfferedInterface(ManagementCI.class);
		this.addOfferedInterface(PublicationCI.class);
		this.addRequiredInterface(ReceptionCI.class);
		
		/**---------------- PORTS CREATION --------------------*/
		this.mipPublisher = new ManagementCInBoundPort(managInboundPortPublisher,this);
		this.mipSubscriber = new ManagementCInBoundPort(managInboundPortSubscriber,this);
		this.publicationInboundPort = new PublicationCInBoundPort(publicationInboundPortURI, this);
		
		
		/**-------------- PUBLISH PORT IN REGISTER ------------*/
		this.mipPublisher.publishPort(); 
		this.mipSubscriber.publishPort(); 
		this.publicationInboundPort.publishPort();
		
		
		/**---------------------- THREADS ---------------------*/
		indexWrite = createNewExecutorService("group1-thread", 5, true);
		indexRead = createNewExecutorService("group2-thread", 5, false);
		
		
	}
	
	
	/**-----------------------------------------------------
	 * -------------------- LIFE CYCLE ---------------------
	 ------------------------------------------------------*/
	/**
	 * 
	 */
	@Override
	public void	start() throws ComponentStartException {
		super.start() ;
		this.logMessage("starting Broker component.") ;
		
	}
	
	@Override
	public void	execute() throws Exception{
		super.execute();
		
	}
	

	@Override
	public void	finalise() throws Exception{
		this.logMessage("stopping broker component.") ;
		this.printExecutionLogOnFile("broker");
		
		/**------------------ DELETE PORTS --------------------*/
		this.mipPublisher.unpublishPort(); 
		this.mipSubscriber.unpublishPort(); 
		this.publicationInboundPort.unpublishPort();
		

			
		
		super.finalise();
	}
	
	
	
	/**======================================================================================
	 * ================================== PUBLICATIONCI =====================================
	 ======================================================================================*/

	@Override
	public void publish(MessageI m, String topic) throws Exception {
		ArrayList<MessageI> n;
		if(isTopic(topic)) {   
			readLock.lock();
			n = topics.get(topic);
			readLock.unlock();
		}else {
			n = new ArrayList<>();
		}
		writeLock.lock();
		n.add(m);
		topics.put(topic, n);
		writeLock.unlock();

		this.sendMessage(m, topic);
		this.logMessage("Broker: Message publié dans "+topic);
	}

	@Override
	public void publish(MessageI m, String[] listTopics) throws Exception {
		for(String t: listTopics) {
			this.publish(m,t);
		}
	}

	@Override
	public void publish(MessageI[] ms, String topic) throws Exception {
		for(MessageI m: ms)
			this.publish(m, topic);
	}

	@Override
	public void publish(MessageI[] ms, String[] listTopics) throws Exception {
		for(String t: listTopics) {  //listTopics: local donc pas besoin de lock
			this.publish(ms,t);
		}
	}

	
	/**======================================================================================
	 * =================================== MANAGEMENTCI =====================================
	 ======================================================================================*/
	@Override
	public void createTopic(String topic) throws Exception {
		if(!isTopic(topic)) {
			writeLock.lock();
			topics.put(topic, new ArrayList<MessageI>());
			writeLock.unlock();
		}
	}

	@Override
	public void createTopics(String[] listTopics) throws Exception {
		for(String t : listTopics) //listTopics: local donc pas besoin de lock
			createTopic(t);
	}

	@Override
	public void destroyTopic(String topic) throws Exception {
		if(isTopic(topic)) {
			writeLock.lock();
			topics.remove(topic); // /!\ être sur d'avoir délivrer les messages avant la destruction du topic. 
			subscriptions.remove(topic);
			writeLock.unlock();
		}
	}

	@Override
	public boolean isTopic(String topic) throws Exception {
		try {
			readLock.lock();
			return topics.containsKey(topic);
		}finally {
			readLock.unlock();
		}
	}

	@Override
	public String[] getTopics() throws Exception {
		try {
			readLock.lock();
			Set<String> keys = topics.keySet();
			String[] tops = keys.toArray(new String[0]);
			return tops;
		}finally {
			readLock.unlock();
		}
	}


	/**------------------------------------------------------
	 * ----------------- (UN)SUBSCRIBE ----------------------
	 -------------------------------------------------------*/
	/**
	 * @param topic where the subscriber want to subscribe
	 * @param inboundPortURI of the Subscriber
	 * @throws Exception 
	 */
	@Override
	public void subscribe(String topic, String inboundPortURI) throws Exception {
		this.subscribe(topic, null, inboundPortURI);
	}

	/**
	 * @param listTopics where the subscriber want to subscribe
	 * @param inboundPortURI of the Subscriber
	 * @throws Exception 
	 */
	@Override
	public void subscribe(String[] listTopics, String inboundPortURI) throws Exception {
		for(String t: listTopics)			//listTopics: local donc pas besoin de lock
			this.subscribe(t, null, inboundPortURI);
	}

	/**
	 * @param topic where the subscriber want to subscribe
	 * @param filter of the topic
	 * @param inboundPortURI of the Subscriber
	 * @throws Exception 
	 */
	@Override
	public void subscribe(String topic, MessageFilterI filter, String inboundPortURI) throws Exception {
		if(!isTopic(topic))
			this.createTopic(topic);

		if(!isSubscribe(topic, inboundPortURI)) {
			ArrayList<Client> subs;
			
			readLock.lock();
			if (subscriptions.containsKey(topic)) //Si le topic contiens déjà des subscibers
				subs = subscriptions.get(topic);  //On les recupèrent (les abonnées)
			else
				subs = new ArrayList<>();	      //Sinon on crée une nouvelle liste associé au topic
			readLock.unlock();
			
			Client monSub = getSubscriber(inboundPortURI);
			
			if(monSub==null) { // Si le sub n'est pas dans la liste officiel des subscibers existants
				String portURI = "uri-"+cpt++;
				monSub = new Client(inboundPortURI, new ReceptionCOutBoundPort(portURI, this));  // Je crée le sub avec un port unique
				monSub.getPort().publishPort();
				this.doPortConnection(
						monSub.getOutBoundPortURI(),
						inboundPortURI, 
						ReceptionConnector.class.getCanonicalName()) ;
				
				writeLock.lock();
				subscribers.add(monSub); // J'ajoute le sub nouvellement crée dans la liste officiel des subscibers
				writeLock.unlock();
			}
			
			if(filter != null)
				monSub.setFilter(filter,topic); 
			
			// Je l'ajoute dans la liste de ceux abonnée au topic
			writeLock.lock();
			subs.add(monSub);
			subscriptions.put(topic, subs);
			writeLock.unlock();
			
			this.logMessage("Subscriber "+inboundPortURI+" subscribe to topic "+topic);
		}else {
			this.logMessage("Subscriber "+inboundPortURI+" already subscribe to topic "+topic);
		}


	}

	/**
	 * @param topic where the subscriber want to modify his filter
	 * @param newFilter is the new filter 
	 * @param inboundPortURI of the Subscriber
	 * @throws Exception 
	 */
	@Override
	public void modifyFilter(String topic, MessageFilterI newFilter, String inboundPortURI) throws Exception {
		writeLock.lock();
		for(Client s : subscriptions.get(topic)) {
			if(s.getInBoundPortURI().equals(inboundPortURI)) {
				s.setFilter(newFilter, topic);
				break;
			}
		}	
		writeLock.unlock();
	}

	/**
	 * @param topic where the subscriber want to unsubscribe
	 * @param inboundPortURI of the Subscriber
	 * @throws Exception 
	 */
	@Override
	public void unsubscribe(String topic, String inboundPortURI) throws Exception {

		Client trouve = null;
		readLock.lock();
		ArrayList<Client> clients = subscriptions.get(topic);
		for(Client s : clients) {
			if(s.getInBoundPortURI().equals(inboundPortURI)) {
				trouve = s;
				break;
			}
		}
		readLock.unlock();

		if(trouve != null) {
			this.logMessage("Unsubscribe of "+trouve.getOutBoundPortURI()+" for the topic "+topic);
			writeLock.lock();
			clients.remove(trouve);
			subscriptions.put(topic, clients);
			writeLock.unlock();
		}else {
			this.logMessage(inboundPortURI+" already unsubscribe");
		}

	}
	
	/**---------------------------------------------*/
	
	/**
	 * @param topic : the name of the topic
	 * @param inboundPortURI : the name of the inboundPortURI
	 * @return Check if the subscriber is already in the hashmap subs
	 * @throws Exception 
	 */
	private boolean isSubscribe(String topic, String inboundPortURI) throws Exception {
		try {
			readLock.lock();
			if(subscriptions.containsKey(topic)) {
				for(Client s : subscriptions.get(topic)) {
					if(s.getInBoundPortURI().equals(inboundPortURI))
						return true;
				}
			}
			return false;
		}finally {
			readLock.unlock();
		}
	}
	
	/**
	 * @param inboundPortURI : the name of the  inboundPortURI
	 * @return the subscriber if he exists else null
	 * @throws Exception 
	 */
	private Client getSubscriber(String inboundPortURI) throws Exception { 
		try {
			readLock.lock();
			for(Client sub: subscribers) {
				if(sub.getInBoundPortURI().equals(inboundPortURI))
					return sub;
			}	
			return null;
		}finally {
			readLock.unlock();
		}
	}

	/**=====================================================================================
	 * =================================== RECEPTIONCI =====================================
	 ======================================================================================*/
	
	/**
	 * Notify Subscribers and send them the message
	 * @param m the message to send
	 * @param topic where the message is publish
	 * @throws Exception
	 */
	public void sendMessage(MessageI m, String topic) throws Exception {
		readLock.lock();

		handleRequestAsync(indexRead,
				owner -> {if(subscriptions.containsKey(topic)) { 
					for(Client sub : subscriptions.get(topic)) {
						if(sub.hasFilter(topic)) {
							MessageFilterI f = sub.getFilter(topic);
							if (!f.filter(m))
								sub.getPort().acceptMessage(m);
						}else {
							try {
								sub.getPort().acceptMessage(m);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}; return null;}) ;


		readLock.unlock();

	}
	
}

