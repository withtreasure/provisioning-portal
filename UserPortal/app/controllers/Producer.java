package controllers;

import java.util.LinkedList;
import java.util.List;

import javax.persistence.Query;

import models.sc_offer;
import models.sc_offers_subscriptions;

import org.jclouds.abiquo.AbiquoContext;
import org.jclouds.abiquo.domain.cloud.VirtualAppliance;
import org.jclouds.abiquo.domain.cloud.VirtualDatacenter;
import org.jclouds.abiquo.domain.cloud.VirtualMachine;

import controllers.ProducerDAO;
import controllers.ProducerLocal;
import controllers.ProducerRemote;
import play.Logger;
import play.db.jpa.JPA;
import play.mvc.Controller;
import portal.util.AbiquoUtils;
import portal.util.Context;

/**
 * 
 * @author Harpreet Kaur
 * If user logged in has CLOUD_ADMIN role ,he can creates service cataloge entries .
 * he chooses entries from abiquo flex GUI client (ProducerRemote) and add to portal database (ProducerLocal)
 */
public class Producer extends Controller {
	
	public static void poe()
	{
		String user =session.get("username");
		if ( user != null)
		{
				Iterable<VirtualDatacenter> vdc_list = ProducerRemote.listVirtualDatacenters();
				render(vdc_list,user);
		}
		else 
		{			
			flash.error("You are not connected.Please Login");
			Login.login_page();
		}
		//ProducerLocal.subscribedOffers();
	}
	
	public static void listAllOffers ( Integer id_vdc , String service_level  )
	{
		Iterable<VirtualDatacenter> vdc_list = null;
		VirtualDatacenter virtualDatacenter = null;
		List<VirtualAppliance> vaList = null;
		List<VirtualAppliance> vaWithVm = new LinkedList<VirtualAppliance>();
		String user =session.get("username");
		String password =session.get("password");
		if ( user != null )
		{
			AbiquoContext context = Context.getContext(user,password);
			AbiquoUtils.setAbiquoUtilsContext(context);
			try {
					if (id_vdc != null )
					{
						vdc_list = AbiquoUtils.getAllVDC();
						 virtualDatacenter = AbiquoUtils.getVDCDetails(id_vdc);
						if (virtualDatacenter != null )
						{
							vaList = virtualDatacenter.listVirtualAppliances();
							for ( VirtualAppliance virtualAppliance : vaList )
							{
								 Integer va_id = virtualAppliance.getId();
								 Query query = JPA.em().createNamedQuery("getOfferDetails");
								 query.setParameter(1,va_id);
								 List<sc_offer> scOffer = query.getResultList();
								 if ( scOffer.size() == 0)
								 {
								 	List<VirtualMachine> vmList = virtualAppliance.listVirtualMachines();
								 	if (vmList.size()>0 )
								 	{
								 			vaWithVm.add(virtualAppliance);
								 			
								 	}
								 }
							}
						}
							
					}
				  List<sc_offers_subscriptions> resultSet = ProducerDAO.getSubscribedOffersGroupByServiceLevels();
				  List<sc_offers_subscriptions> resultSet1 = ProducerDAO.getSubscribedOffers(service_level);
				  Logger.info(" resultSet1 size " + resultSet1);
				  Logger.info(" -----INSIDE PRODUCER DISPLAYOFFER()------");
				  render("/Producer/offerList.html",resultSet,resultSet1,user, vaWithVm, virtualDatacenter , vdc_list);
				} 
				catch(Exception e)
				{
							flash.error("Unable to create context");
						   // e.printStackTrace();
							//Logger.info(" -----EXITING PRODUCER VMDETAILS()------" + e.printStackTrace(), "");
							render("/ProducerRemote/listVDC.html");
					
					
				}
				finally{
							flash.clear();
							if (context!= null)
								context.close();
				}
			
	}
	else 
	{
		
		flash.error("You are not connected.Please Login");
		Login.login_page();
	}
	}
}