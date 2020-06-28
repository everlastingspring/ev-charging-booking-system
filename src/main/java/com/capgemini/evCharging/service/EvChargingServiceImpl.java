package com.capgemini.evCharging.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capgemini.evCharging.bean.Booking;
import com.capgemini.evCharging.bean.Credential;
import com.capgemini.evCharging.bean.Employee;
import com.capgemini.evCharging.bean.Machine;
import com.capgemini.evCharging.bean.MachineDetailKey;
import com.capgemini.evCharging.bean.MachineDetailValue;
import com.capgemini.evCharging.bean.MachineDetails;
import com.capgemini.evCharging.bean.Station;
import com.capgemini.evCharging.bean.enums.BookingStatus;
import com.capgemini.evCharging.bean.enums.MachineDetailStatus;
import com.capgemini.evCharging.bean.enums.MachineStatus;
import com.capgemini.evCharging.bean.enums.MachineType;
import com.capgemini.evCharging.bean.enums.SlotDuration;
import com.capgemini.evCharging.dao.BookingDao;
import com.capgemini.evCharging.dao.CredentialDao;
import com.capgemini.evCharging.dao.EmployeeDao;
import com.capgemini.evCharging.dao.MachineDao;
import com.capgemini.evCharging.dao.StationDao;
import com.capgemini.evCharging.exception.EvChargingException;

@Service
public class EvChargingServiceImpl implements EvChargingService {



	@Autowired
	EmployeeDao employeeRepo;

	@Autowired
	CredentialDao credentialRepo;


	@Autowired
	StationDao stationRepo;

	@Autowired
	MachineDao machineRepo;

	@Autowired
	BookingDao bookingRepo;


	@Override
	public Employee areCredentialsMatched(String mailId, String password) throws EvChargingException {

		try {
			Optional<Credential> optionalCredential = credentialRepo.findByMailId(mailId);
			if (optionalCredential.isPresent()) {
				Credential credential = optionalCredential.get();
				String hashUserPassword = HashAlgorithmService.hashedPassword(password, credential.getSaltArray());
				if (hashUserPassword.equals(credential.getHashedPassword())) {
					return employeeRepo.findByMailId(mailId).get();
				}
				throw new EvChargingException("Password mismatch");

			} else {
				throw new EvChargingException("Mail Id is not registered!");
			}

		} catch (Exception exception) {
			throw new EvChargingException(exception.getMessage());
			
		}


	}

	@Override
	public Boolean registerEmployee(Employee emp, String password, Boolean isAdmin) throws EvChargingException {

		try {
			Optional<Station> optionalStation = stationRepo.checkIfStationExists(emp.getEmployeeStation().getCity(), emp.getEmployeeStation().getCampusLocation()) ;
			if(optionalStation.isEmpty()) {
				throw new EvChargingException("No such charging station exists, add the station first");
			}
			employeeRepo.save(emp);
			Employee employee = employeeRepo.findByMailId(emp.getMailId()).get();
			Credential credential = new Credential();
			credential.setEmployeeId(employee.getEmployeeId());
			credential.setMailId(emp.getMailId());
			credential.setIsAdmin(isAdmin);

			byte[] salt = HashAlgorithmService.createSalt();
			String hashedPassword = HashAlgorithmService.hashedPassword(password, salt);
			credential.setHashedPassword(hashedPassword);
			credential.setSaltArray(salt);
			System.out.println(credential);
			System.out.println(employee);
			
			credentialRepo.save(credential);

			

			return true;
		} catch (Exception exception) {

			throw new EvChargingException(exception.getMessage());
		}

	}



	@Override
	public List<Station> getStations() {
		return stationRepo.findAll();
	}


	public Integer getPossibleNumberOfBookings(MachineType selectedMachineType, Integer stationId,Date selectedDate) {
		
		List<Machine> machines = getActiveMachinesOfTypeAndStation(selectedMachineType, stationId, selectedDate);
		
		LocalTime endOfTheDayTime = LocalTime.of(23, 59, 59);
		
		
		System.out.println(machines);
		Integer possibleBookings = 0;
		for(Machine machine: machines) {
			
			
			possibleBookings += ((machine.getEndTime().toSecondOfDay()  - machine.getStartTime().toSecondOfDay()) / (machine.getSlotDuration().getValue() * 60));
			if(endOfTheDayTime.equals(machine.getEndTime())) {
				possibleBookings ++;
			}
			
		}
		return possibleBookings;
	}

	@Override
	public Date getNextAvailableBookingDate(MachineType selectedMachineType, Integer selectedStationId) throws EvChargingException {
		
		LocalDate currentDate = LocalDate.now();
		LocalDate selectedDate;
		Date sqlFormattedDate = new Date(System.currentTimeMillis());
		Boolean isFound = false;
		
		
			
		for(selectedDate = currentDate; !isFound ; selectedDate =  selectedDate.plusDays(1)) {
			
			sqlFormattedDate = Date.valueOf(selectedDate);
//			String quotedDate = "\'" + sqlFormattedDate + "\'";
			Integer currentBookings =  bookingRepo.getBookingsAtStationOnDateWithType(selectedStationId, sqlFormattedDate, selectedMachineType);
			Integer possibleBookings = getPossibleNumberOfBookings(selectedMachineType, selectedStationId, sqlFormattedDate);
			System.out.println("Current: " + currentBookings + " " + "possibleBookings" + " " + possibleBookings );
			if(currentBookings < possibleBookings) {
				isFound = true;
				
			} else if(currentBookings > possibleBookings) {
				throw new EvChargingException("Current Active Bookings " + currentBookings + " at "+ selectedStationId + " station with selected machine type "+ selectedMachineType + " on " + selectedDate + "can't be greater than possible bookings" + possibleBookings);
			} 
			
			
		}
		
		return sqlFormattedDate;
		
	}



	public List<Machine> getActiveMachinesOfTypeAndStation(MachineType selectedMachineType, Integer stationId,Date selectedDate) {

//		String quotedDate = "\'" +  selectedDate.toString() + "\'";
		//select * from machine where machine.machineType = 'Level1' and machine.stationId = stationId and machine.duration = duration and machine.machine_status = 'Active' and machine.staring_date <= currentDate;
		return machineRepo.getActiveMachinesOfStationAndType(selectedMachineType, stationId, MachineStatus.ACTIVE, selectedDate);

	}


	public List<Booking> getBookingsOfMachine(Integer machineId, Date selectedDate) {
		//select * from Booking where booking.booked_machine.machineId = machineId and booking.bookedDate = selectedDate and booking.booking_status="Booked";
		return bookingRepo.getBookingsOfMachine(machineId, selectedDate, BookingStatus.BOOKED);
	}


	public MachineDetails updateMachineBookingDetail(Booking booking, MachineDetails machineDetails) throws EvChargingException{

		int mins = (booking.getBookingEndTime().toSecondOfDay() - booking.getBookingStartTime().toSecondOfDay()) / 60;
		SlotDuration slotDuration;
		if(mins == 60) {
			slotDuration = SlotDuration.SIXTY;
		} else if(mins == 30) {
			slotDuration = SlotDuration.THIRTY;
		} else if(mins == 15) {
			slotDuration = SlotDuration.FIFTEEN;
		} else {
			throw new EvChargingException("Booking slot duration is not applicable for " + mins);
		}
		MachineDetailKey detailKey = new MachineDetailKey(booking.getBookingStartTime(), booking.getBookingEndTime(),slotDuration);
		
		ArrayList<MachineDetailValue> machineDetailValues = machineDetails.getMachineDetails().get(detailKey);
		
		for(MachineDetailValue detailValue : machineDetailValues) {

			if(detailValue.getMachineId() == booking.getBookedMachine().getMachineId()) {
				machineDetailValues.remove(detailValue);
				detailValue.setBookedByEmployeeId(booking.getBookingByEmployee().getEmployeeId()) ;
				detailValue.setStatus(MachineDetailStatus.BOOKED);
				machineDetailValues.add(detailValue);
				machineDetails.getMachineDetails().put(detailKey, machineDetailValues);
			}
		}
		return machineDetails;
	}


	@Override
	public MachineDetails getMachineBookingDetail(Date selectedDate, MachineType selectedMachineType, Integer stationId) throws EvChargingException {

		List<Machine> machines =  getActiveMachinesOfTypeAndStation(selectedMachineType,stationId,selectedDate);
		MachineDetails machineDetails = new MachineDetails();
		machineDetails =  Utility.utilityObject.populateMachineDetails(machineDetails,machines);

		for (Machine machine : machines) {
			List<Booking> bookings = getBookingsOfMachine(machine.getMachineId(), selectedDate);

			for(Booking booking : bookings) {

			
				machineDetails = updateMachineBookingDetail(booking, machineDetails);

			}
		}

		return machineDetails;
	}




	@Override
	public List<Booking> bookMachine(Date bookedDate, LocalTime bookingStartTiming, Integer machineId, Integer employeeId) throws EvChargingException {

		
		Machine bookedMachine = Utility.utilityObject.getMachineFromMachineId(machineId, machineRepo);
		
		Optional<Employee> optionalBookedByEmployee = employeeRepo.findById(employeeId);
		if(optionalBookedByEmployee.isEmpty()) {
			throw new EvChargingException("Employee with empId " + employeeId + " not present");
		}
		Optional<Booking> checkedBooking = bookingRepo.checkIfMachineIsNotBooked(machineId, bookedDate, bookingStartTiming);
		if(checkedBooking.isPresent() && checkedBooking.get().getStatus() == BookingStatus.BOOKED) {
			throw new EvChargingException("Machine is already booked by employee with empId " + checkedBooking.get().getBookingByEmployee().getEmployeeId());
		}
		
		Booking booking = new Booking();
		booking.setBookedMachine(bookedMachine);
		booking.setBookedDate(bookedDate);
		booking.setBookingStartTime(bookingStartTiming);
		booking.setBookingEndTime(bookingStartTiming.plusMinutes(bookedMachine.getSlotDuration().getValue()));
		booking.setBookingByEmployee(optionalBookedByEmployee.get());
		booking.setStatus(BookingStatus.BOOKED);
		bookingRepo.save(booking);

		return bookingRepo.findAll();


	}

	@Override
	public List<Booking> getEmployeeAllBookings(Integer employeeId) throws EvChargingException {
		List<Booking> bookings = bookingRepo.getAllBookingsByEmployee(employeeId);

		if(bookings.isEmpty()) {
			throw new EvChargingException("User with id " + employeeId + " has no booking");
		}

		return bookings;
	}
	
	@Override
	public List<Booking> getEmployeeCurrentBookings(Integer employeeId) throws EvChargingException {
		
			Date currentDate = new Date(System.currentTimeMillis());
			//String quotedDate = "\'" +  currentDate.toString() + "\'";
			LocalTime currentTime = LocalTime.parse(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
			//String quotedTime = "\'" + currentTime.toString() + "\'"; 
		
			System.out.println(currentDate + " " + currentTime);
			List<Booking> bookings = bookingRepo.getCurrentBookingsByEmployee(employeeId, currentDate, currentTime);

			if(bookings.isEmpty()) {
				throw new EvChargingException("User with id " + employeeId + " has no current booking");
			}

			return bookings;
			
	}
	
	@Override
	public List<Booking> rescheduleBooking(Integer ticketNo, Date rescheduledBookedDate, LocalTime rescheduledBookingStartTiming, Integer machineId, Integer employeeId) throws EvChargingException {
		Booking booking = Utility.utilityObject.getBookingFromTicketNo(ticketNo,bookingRepo);

		booking.setStatus(BookingStatus.RESCHEDULED);

		bookMachine(rescheduledBookedDate, rescheduledBookingStartTiming, machineId, employeeId);
		bookingRepo.save(booking);
		
		return bookingRepo.findAll();
	}


	@Override
	public List<Booking> cancelBooking(Integer ticketNo) throws EvChargingException {

		Booking booking = Utility.utilityObject.getBookingFromTicketNo(ticketNo,bookingRepo);

		booking.setStatus(BookingStatus.CANCELLED);

		bookingRepo.save(booking);
		return bookingRepo.findAll();

	}

	@Override
	public MachineDetails getMachineBookingDetail(Date selectedDate, SlotDuration selectedDuration, Integer stationId) throws EvChargingException {
		return null;
	}
	

	@Override
	public List<Machine> addMachines(Integer stationId, List<Machine> machines) throws EvChargingException {

		Station station = Utility.utilityObject.getStationFromStationId(stationId, stationRepo);
		//		Machine
		for (Machine machine : machines) {
			machine.setMachineStation(station);
			machine.setMachineStatus(MachineStatus.ACTIVE);
			machineRepo.save(machine);
		}
		
		return machineRepo.findAll();

	}



	@Override
	public List<Machine> removeMachine(Integer machineId) throws EvChargingException {
		
		if (!machineRepo.existsById(machineId)) {
			throw new EvChargingException("Machine with " + machineId + "doesn't exist");
		}
		machineRepo.deleteById(machineId);
 		return machineRepo.findAll();
	}

	@Override
	public Machine haltMachine(Integer machineId, Date newStartDate) throws EvChargingException {
		if(!machineRepo.existsById(machineId)) {
			throw new EvChargingException("Machine with " + machineId + "doesn't exist");
		}
		Machine machine = Utility.utilityObject.getMachineFromMachineId(machineId, machineRepo);
		machine.setStartingDate(newStartDate);
		machineRepo.save(machine);
		return machineRepo.findById(machineId).get();
	}
	
	@Override
	public Machine haltMachine(Integer machineId, Date newStartDate, LocalTime newStartTime, LocalTime newEndTime)
			throws EvChargingException {
		if(!machineRepo.existsById(machineId)) {
			throw new EvChargingException("Machine with " + machineId + "doesn't exist");
		}
		Machine machine = Utility.utilityObject.getMachineFromMachineId(machineId, machineRepo);
		machine.setStartingDate(newStartDate);
		machine.setStartTime(newStartTime);
		machine.setEndTime(newEndTime);
		machineRepo.save(machine);
		return machineRepo.findById(machineId).get();
	}


	@Override
	public Machine resumeMachine(Integer machineId) throws EvChargingException {
		Machine machine = Utility.utilityObject.getMachineFromMachineId(machineId, machineRepo);
		if (machine.getMachineStatus() == MachineStatus.ACTIVE) {
			throw new EvChargingException("Machine with " + machineId  + "is already in active state");
		}
		machine.setMachineStatus(MachineStatus.ACTIVE);
		machineRepo.save(machine);
		return machineRepo.findById(machineId).get();
	}

	@Override
	public Machine modifyMachine(Machine modifiedMachine) throws EvChargingException {
		
		if(!machineRepo.existsById(modifiedMachine.getMachineId())) {
			throw new EvChargingException("Machine with " + modifiedMachine.getMachineId() + " doesn't exist");
		}
		machineRepo.save(modifiedMachine);
		return Utility.utilityObject.getMachineFromMachineId(modifiedMachine.getMachineId(), machineRepo);
	}

	@Override
	public List<Booking> generateBookingsReport(Integer stationId,Date fromDate, Date toDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Booking> generateMachineBookingsReport(Integer machineId,Date fromDate, Date toDate) {
		
		// TODO Auto-generated method stub
		return null;
	}

	
	
	
	//Non UI Methods
	@Override
	public List<Station> addStation(String city, String campusLocation) {
		Station newStation = new Station();
		newStation.setCity(city);
		newStation.setCampusLocation(campusLocation);
		stationRepo.save(newStation);
		
		return stationRepo.findAll();
	}

	
	
	
	


}











