/**
 * @author Harpreet Kaur
 *  Producer component of provisioning portal .
 *  Includes methods that talks to Abiquo server and retrieve required information about vdc, vapp and vm
 */

package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.DateParts;
import models.Nodes;
import models.Nodes_Resources;
import models.sc_offer;
import models.sc_offers_subscriptions;

import org.jclouds.abiquo.AbiquoContext;
import org.jclouds.abiquo.domain.cloud.HardDisk;
import org.jclouds.abiquo.domain.cloud.VirtualAppliance;
import org.jclouds.abiquo.domain.cloud.VirtualDatacenter;
import org.jclouds.abiquo.domain.cloud.VirtualMachine;
import org.jclouds.abiquo.domain.cloud.VirtualMachineTemplate;
import org.jclouds.abiquo.domain.network.PrivateNetwork;

import play.Logger;
import play.data.validation.Required;
import play.data.validation.Valid;
import play.data.validation.Validation;
import play.db.jpa.Blob;
import play.libs.MimeTypes;
import play.mvc.Controller;
import portal.util.AbiquoUtils;
import portal.util.Context;

import com.abiquo.model.enumerator.HypervisorType;

public class ProducerRemote extends Controller {

	/**
	 * 
	 * @return list of virtualdatacenters available for current user's ( logged
	 *         in as CLOUD_ADMIN) enterprise
	 */

	public static Iterable<VirtualDatacenter> listVirtualDatacenters() {
		Logger.info("-----INSIDE PRODUCER LISTVDC()------ ");

		String user = session.get("username");
		String password = session.get("password");

		/*
		 * String user = CurrentUserContext.getUser();
		 * Logger.info("Session user in listVDC(): "+ user); AbiquoContext
		 * context = CurrentUserContext.getContext(); AbiquoContext context =
		 * contextProp.getContext(); Logger.info("Created context :" + context
		 * );
		 */

		AbiquoContext context = Context.getContext(user, password);
		AbiquoUtils.setAbiquoUtilsContext(context);
		Iterable<VirtualDatacenter> vdc_list = AbiquoUtils.getAllVDC();
		return vdc_list;

	}

	/**
	 * Lists virtual machines for selected virtual appliance
	 * 
	 * @param id_vdc_param
	 *            The virtual datacentre id
	 * @param id_va_param
	 *            The virtual appliance id
	 */
	public static void listVM(final Integer id_vdc_param,
			final Integer id_va_param) {
		Logger.info(" -----INSIDE PRODUCER LISTVM()------");

		String user = session.get("username");
		String password = session.get("password");
		if (user != null) {
			AbiquoContext context = Context.getContext(user, password);
			AbiquoUtils.setAbiquoUtilsContext(context);
			try {
				VirtualDatacenter virtualDatacenter = AbiquoUtils
						.getVDCDetails(id_vdc_param);
				VirtualAppliance virtualAppliance = AbiquoUtils.getVADetails(
						id_vdc_param, id_va_param);

				if (virtualAppliance != null) {
					List<VirtualMachine> vmList = virtualAppliance
							.listVirtualMachines();

					for (VirtualMachine virtualMachine : vmList) {
						virtualMachine.listAttachedNics();
					}
					
					List<sc_offer> sc_offers = ProducerDAO.getOfferDetails(id_va_param);
					render(vmList, virtualAppliance, virtualDatacenter, user, sc_offers);
				}

				else {
					String msg = " No Virtual Machine to display!!";
					render(msg, user);
				}
				Logger.info(" -----EXITING PRODUCER LISTVM()------");
			} catch (Exception e) {
				flash.error("Unable to create contaxt");
				render();
				// e.printStackTrace();

			} finally {
				if (context != null) {
					context.close();
				}

			}
		} else {

			flash.error("You are not connected.Please Login");
			Login.login_page();
		}

	}

	/**
	 * Displays details about virtual machine such as ram, cpu, hardisks etc.
	 * 
	 * @param vdc
	 *            The virtual datacentre id
	 * @param va
	 *            The virtual appliance id
	 * @param vm
	 *            The virtual machine id
	 */
	public static void vmDetails(final Integer vdc, final Integer va,
			final Integer vm) {
		Logger.info(" -----INSIDE PRODUCER VMDETAILS()------");

		String user = session.get("username");
		String password = session.get("password");
		Logger.info("Session user in vmDetails(): " + user);
		if (user != null) {
			AbiquoContext context = Context.getContext(user, password);

			try {
				VirtualMachine virtualMachine = AbiquoUtils.getVMDetails(vdc,
						va, vm);
				// context.getCloudService().getVirtualDatacenter(vdc).getVirtualAppliance(va).getVirtualMachine(vm);
				Integer cpu = virtualMachine.getCpu();
				Integer ram = virtualMachine.getRam();
				long hd = virtualMachine.getHdInBytes();
				List<HardDisk> harddisk = virtualMachine
						.listAttachedHardDisks();

				VirtualMachineTemplate template = virtualMachine.getTemplate();
				// String template_path = template.getIconUrl();
				String template_name = template.getName();
				String template_path = template.getPath();
				render(vdc, va, vm, cpu, ram, hd, harddisk, template_name,
						template_path, user);
				Logger.info(" -----EXITING PRODUCER VMDETAILS()------");

			} catch (Exception e) {
				flash.error("Unable to create contaxt");
				render();
			} finally {
				if (context != null) {
					context.close();
				}
			}

		} else {

			flash.error("You are not connected.Please Login");
			Login.login_page();
		}
	}

	/*
	 * static String setDate(DateParts date) { String year = date.getYear();
	 * String month = date.getMonth(); String day = date.getDay(); String
	 * gendate = year+"-"+month+"-"+day; Logger.info(gendate); return gendate; }
	 */

	/**
	 * Create service catalog entry ( saving to portal database)
	 * 
	 * @param sc_offers
	 * @param icon
	 * @param image
	 * @param offerSubscription
	 * @param date
	 */
	public static void addToServiceCatalog(@Valid final sc_offer sc_offers,
			@Required final File icon, final File image,
			final sc_offers_subscriptions offerSubscription,
			final DateParts date) {

		if (Validation.hasErrors()) {
			flash.error("Sorry! Please enter valid data. See errors inline. Icon is required. Max characters for : Short Description - 30 and Long Description - 255 ");
			params.flash();
			Validation.keep();
			configure(sc_offers.getIdVirtualDataCenter_ref(),
					sc_offers.getSc_offer_id());

		} else {
			extracted();
			String user = session.get("username");
			String password = session.get("password");
			if (user != null) {
				Logger.info("Session user in addToServiceCatalog(): " + user);
				Logger.info(" and  vdc "
						+ sc_offers.getIdVirtualDataCenter_ref()
						+ "  & va_id : " + sc_offers.getSc_offer_id());
				Logger.info(" lease period "
						+ offerSubscription.getLease_period());
				/*
				 * Logger.info(" start date" +
				 * offerSubscription.getStart_date());
				 * Logger.info(" expiry date" +
				 * offerSubscription.getExpiration_date());
				 * 
				 * Logger.info(" Year : " + date.getYear());
				 * Logger.info(" Year : " + date.getMonth());
				 * Logger.info(" Year : " + date.getDay());
				 * 
				 * String start_date = setDate(date); DateFormat formatter ;
				 * Date datee ;
				 */
				Integer vdc_id_param = sc_offers.getIdVirtualDataCenter_ref();
				Integer id_va_param = sc_offers.getSc_offer_id();
				String lease_period = offerSubscription.getLease_period();

				AbiquoContext context = Context.getContext(user, password);
				try {
					/*
					 * formatter = new SimpleDateFormat("yyyy-M-d"); datee =
					 * (Date)formatter.parse(start_date);
					 */
					AbiquoUtils.setAbiquoUtilsContext(context);
					VirtualDatacenter virtualDC = AbiquoUtils
							.getVDCDetails(vdc_id_param);
					VirtualAppliance va = AbiquoUtils.getVADetails(
							vdc_id_param, id_va_param);

					Integer id_datacenter = virtualDC.getDatacenter().getId();
					HypervisorType hypervisor = virtualDC.getHypervisorType();

					PrivateNetwork network = PrivateNetwork.builder(context)
							.name("10.80.0.0").gateway("10.80.0.1")
							.address("10.80.0.0").mask(22).build();

					sc_offer scOffer = new sc_offer();
					scOffer.setSc_offer_id(va.getId());
					scOffer.setSc_offer_name(va.getName());
					if (icon != null) {
						scOffer.setIcon_name(sc_offers.getIcon_name());
						scOffer.setIcon_name(icon.getName());
						scOffer.setIcon(new Blob());
						scOffer.getIcon().set(new FileInputStream(icon),
								MimeTypes.getContentType(icon.getName()));
					}
					if (image != null) {
						scOffer.setImage(new Blob());
						scOffer.getImage().set(new FileInputStream(image),
								MimeTypes.getContentType(image.getName()));
					}
					scOffer.setShort_description(sc_offers
							.getShort_description());
					scOffer.setDatacenter(id_datacenter);
					scOffer.setHypervisorType(hypervisor.toString());
					scOffer.setDefault_network_type(network.getAddress());
					scOffer.setId_VirtualDatacenter_ref(va
							.getVirtualDatacenter().getId());
					
					if (offerSubscription.getLease_period().equals("100 years")) scOffer.setService_type("Infinite");
					else scOffer.setService_type("Expire");
					scOffer.setVirtualDatacenter_name(virtualDC.getName());

					Set<Nodes> node_set = new HashSet<Nodes>();
					Nodes node = null;
					List<VirtualMachine> vmlist_todeploy = va
							.listVirtualMachines();
					for (VirtualMachine aVM : vmlist_todeploy) {
						VirtualMachineTemplate vm_template_todeploy = aVM
								.getTemplate();
						// String template_path =
						// vm_template_todeploy.getPath();
						// String vmName = aVM.getName();
						int cpu = aVM.getCpu();
						int ram = aVM.getRam();
						// Long hd = aVM.getHdInBytes();
						String description = aVM.getDescription();
						node = new Nodes();
						node.setId_node(aVM.getId());
						node.setCpu(cpu);
						node.setRam(ram);
						node.setIdImage(vm_template_todeploy.getId());
						node.setIcon("icon");
						node.setDescription(description);
						node.setNode_name(vm_template_todeploy.getName());
						Logger.info(" description : "
								+ sc_offers.getDescription());
						scOffer.setDescription(sc_offers.getDescription());
						node_set.add(node);
						Set<Nodes_Resources> node_resource_set = new HashSet<Nodes_Resources>();

						List<HardDisk> attached_disks = aVM
								.listAttachedHardDisks();
						for (HardDisk hdisk : attached_disks) {
							// HardDisk hdisk = attacheddisks_it.next();
							Long size = hdisk.getSizeInMb();
							Integer sequence = hdisk.getSequence();
							Logger.info(" hard disk sequence :" + sequence);
							Nodes_Resources node_resource = new Nodes_Resources();
							node_resource.setSequence(sequence);
							node_resource.setResourceType(17);
							node_resource.setValue(size);
							node_resource.save();
							node_resource_set.add(node_resource);

						}
						node.setResources(node_resource_set);
					}

					scOffer.setNodes(node_set);
					scOffer.setState("PUBLISHED");
					sc_offers_subscriptions offerSub = new sc_offers_subscriptions();
					offerSub.setSc_offer(scOffer);
					// offerSub.setStart_date(datee);
					offerSub.setService_level(virtualDC.getName());
					offerSub.setLease_period(lease_period);
					offerSub.save();
					Logger.info("-----------EXITING ADDTOSERVICECATALOG()------------");
					render(user);
					// listVDC();
				} catch (Exception e) {

					Logger.warn(e,
							"EXCEPTION OCCURED IN addToServiceCAtalog()",
							vdc_id_param);
					flash.error("Entry already exists");
					render();
				} finally {
					if (context != null) {
						context.close();
					}
				}
			} else {

				flash.error("You are not connected.Please Login");
				Login.login_page();
			}

		}
	}

	private static void extracted() {
		Logger.info(" -----INSIDE PRODUCER ADDTOSERVICECATALOG()------");
	}

	public static void configure(final Integer vdc_id_param,
			final Integer id_va_param) {
		Logger.info(" -----INSIDE PRODUCER CONFIGURE()------");

		String user = session.get("username");
		String password = session.get("password");
		if (user != null) {
			VirtualDatacenter virtualDC = null;
			VirtualAppliance va = null;
			List<VirtualMachine> vmList = null;

			AbiquoContext context = Context.getContext(user, password);
			try {
				AbiquoUtils.setAbiquoUtilsContext(context);
				virtualDC = AbiquoUtils.getVDCDetails(vdc_id_param);

				if (virtualDC != null) {
					Logger.info(" virtualDC  : " + virtualDC.getName());
					va = AbiquoUtils.getVADetails(vdc_id_param, id_va_param);
					if (va != null) {
						Logger.info(" va  : " + virtualDC.getName());
						vmList = va.listVirtualMachines();
					}
					Logger.info(" -----EXITING PRODUCER CONFIGURE()------");
					render(va, virtualDC, vmList, user);
				} else {
					flash.error("Unable to retrieve virtual datacenter");
					Producer.poe();
				}

			} catch (Exception e) {
				flash.error("Oops !!!.........Unable to retrieve virtual datacenter");
				Producer.poe();

			} finally {
				flash.clear();
				if (context != null) {
					context.close();
				}
			}
		} else {

			flash.error("You are not connected.Please Login");
			Login.login_page();
		}
	}

}