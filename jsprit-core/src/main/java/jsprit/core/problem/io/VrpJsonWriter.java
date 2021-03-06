/*******************************************************************************
 * Copyright (C) 2014  Stefan Schroeder
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package jsprit.core.problem.io;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import jsprit.core.algorithm.VehicleRoutingAlgorithm;
import jsprit.core.algorithm.box.GreedySchrimpfFactory;
import jsprit.core.analysis.SolutionAnalyser;
import jsprit.core.problem.VehicleRoutingProblem;
import jsprit.core.problem.job.Job;
import jsprit.core.problem.job.Pickup;
import jsprit.core.problem.job.Service;
import jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import jsprit.core.problem.solution.route.VehicleRoute;
import jsprit.core.problem.solution.route.activity.TimeWindow;
import jsprit.core.problem.solution.route.activity.TourActivity;
import jsprit.core.problem.vehicle.Vehicle;
import jsprit.core.problem.vehicle.VehicleImpl;
import jsprit.core.problem.vehicle.VehicleType;
import jsprit.core.problem.vehicle.VehicleTypeImpl;
import jsprit.core.util.Coordinate;
import jsprit.core.util.Solutions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by stefan on 03.11.14.
 */
public class VrpJsonWriter {

    private final VehicleRoutingProblem vrp;

    private final VehicleRoutingProblemSolution solution;

    private SolutionAnalyser solutionAnalyzer;

    public VrpJsonWriter(VehicleRoutingProblem vrp) {
        this.vrp = vrp;
        this.solution = null;
    }

    public VrpJsonWriter(VehicleRoutingProblem vrp, VehicleRoutingProblemSolution solution, SolutionAnalyser.DistanceCalculator distanceCalculator){
        this.vrp = vrp;
        this.solution = solution;
        this.solutionAnalyzer = new SolutionAnalyser(vrp,solution,distanceCalculator);
    }

    public String toString(){
        StringWriter stringWriter = new StringWriter();
        try {
            JsonGenerator jsonGenerator = new JsonFactory().createGenerator(stringWriter);
            if(solution == null) writeProblem(jsonGenerator);
            else writeSolution(jsonGenerator);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return stringWriter.toString();
    }



    public void write(File jsonFile){
        try {
            JsonGenerator jsonGenerator = new JsonFactory().createGenerator(new FileOutputStream(jsonFile), JsonEncoding.UTF8);
            if(solution == null) writeProblem(jsonGenerator);
            else writeSolution(jsonGenerator);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void writeSolution(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());
        jsonGenerator.writeStartObject();
        writeSolution_(jsonGenerator);
        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
        jsonGenerator.close();
    }

    private void writeSolution_(JsonGenerator jsonGenerator) {
        try {
            jsonGenerator.writeNumberField(JsonConstants.Solution.COSTS,solution.getCost());
            jsonGenerator.writeNumberField(JsonConstants.Solution.FIXED_COSTS,solutionAnalyzer.getFixedCosts());
            jsonGenerator.writeNumberField(JsonConstants.Solution.VARIABLE_COSTS, solutionAnalyzer.getVariableTransportCosts());
            jsonGenerator.writeNumberField(JsonConstants.Solution.DISTANCE,solutionAnalyzer.getDistance());
            jsonGenerator.writeNumberField(JsonConstants.Solution.TIME, solutionAnalyzer.getTransportTime());
            jsonGenerator.writeNumberField(JsonConstants.Solution.NO_ROUTES,solution.getRoutes().size());
            jsonGenerator.writeNumberField(JsonConstants.Solution.NO_UNASSIGNED,solution.getUnassignedJobs().size());

            jsonGenerator.writeArrayFieldStart(JsonConstants.Solution.ROUTES);
            for(VehicleRoute route : solution.getRoutes()){
                jsonGenerator.writeStartObject();
                jsonGenerator.writeNumberField(JsonConstants.Solution.Route.FIXED_COSTS,solutionAnalyzer.getFixedCosts(route));
                jsonGenerator.writeNumberField(JsonConstants.Solution.Route.VARIABLE_COSTS,solutionAnalyzer.getVariableTransportCosts(route));
                jsonGenerator.writeNumberField(JsonConstants.Solution.Route.NO_ACTIVITIES,route.getActivities().size());
                jsonGenerator.writeNumberField(JsonConstants.Solution.Route.START_TIME,route.getStart().getEndTime());
                for(TourActivity act : route.getActivities()) {
                    jsonGenerator.writeObjectFieldStart(JsonConstants.Solution.Route.ACTIVITY);
                    jsonGenerator.writeStringField(JsonConstants.Solution.Route.Activity.TYPE,act.getName());
                    if(act instanceof TourActivity.JobActivity){
                        jsonGenerator.writeStringField(JsonConstants.Solution.Route.Activity.SERVICE_ID,((TourActivity.JobActivity) act).getJob().getId());
                    }
                    else throw new IllegalStateException("act of class " + act.getClass().toString() + " not supported.");
                    jsonGenerator.writeNumberField(JsonConstants.Solution.Route.Activity.ARR_TIME, act.getArrTime());
                    jsonGenerator.writeNumberField(JsonConstants.Solution.Route.Activity.END_TIME,act.getEndTime());
                    jsonGenerator.writeEndObject();
                }
                jsonGenerator.writeNumberField(JsonConstants.Solution.Route.END_TIME, route.getEnd().getArrTime());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
            //unassigned jobs
            jsonGenerator.writeArrayFieldStart(JsonConstants.Solution.Route.UNASSIGNED);
            for(Job j : solution.getUnassignedJobs()){
               jsonGenerator.writeString(j.getId());
            }
            jsonGenerator.writeEndArray();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void writeProblem(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(JsonConstants.FLEET,vrp.getFleetSize().toString());
        writeVehicles(jsonGenerator);
        writeVehicleTypes(jsonGenerator);
        writeServices(jsonGenerator);
        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
        jsonGenerator.close();
    }

    private void writeVehicleTypes(JsonGenerator jsonGenerator) {
        try {
            jsonGenerator.writeArrayFieldStart(JsonConstants.VEHICLE_TYPES);
            Collection<VehicleType> types = getTypes(vrp.getVehicles());

            for(VehicleType type : types){
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField(JsonConstants.Vehicle.Type.ID, type.getTypeId());

                jsonGenerator.writeArrayFieldStart(JsonConstants.Vehicle.Type.CAPACITY);
                for(int i=0;i<type.getCapacityDimensions().getNuOfDimensions();i++){
                    jsonGenerator.writeNumber(type.getCapacityDimensions().get(i));
                }
                jsonGenerator.writeEndArray();

                jsonGenerator.writeNumberField(JsonConstants.Vehicle.Type.FIXED_COSTS, type.getVehicleCostParams().fix);
                jsonGenerator.writeNumberField(JsonConstants.Vehicle.Type.DISTANCE, type.getVehicleCostParams().perDistanceUnit);
                jsonGenerator.writeNumberField(JsonConstants.Vehicle.Type.TIME, type.getVehicleCostParams().perTimeUnit);

                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Collection<VehicleType> getTypes(Collection<Vehicle> vehicles) {
        Set<VehicleType> types = new HashSet<VehicleType>();
        for(Vehicle v : vehicles) types.add(v.getType());
        return types;
    }

    private void writeVehicles(JsonGenerator jsonGenerator) {
        try {
            jsonGenerator.writeArrayFieldStart(JsonConstants.VEHICLES);
            for(Vehicle vehicle : vrp.getVehicles()){
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField(JsonConstants.Vehicle.ID,vehicle.getId());
                jsonGenerator.writeObjectFieldStart(JsonConstants.Vehicle.START_ADDRESS);
                    jsonGenerator.writeStringField(JsonConstants.Address.ID, vehicle.getStartLocationId());
                    jsonGenerator.writeNumberField(JsonConstants.Address.LON, vehicle.getStartLocationCoordinate().getX());
                    jsonGenerator.writeNumberField(JsonConstants.Address.LAT,vehicle.getStartLocationCoordinate().getY());
                jsonGenerator.writeEndObject();
                jsonGenerator.writeBooleanField(JsonConstants.Vehicle.RETURN_TO_DEPOT,vehicle.isReturnToDepot());
                if(!(vehicle.getStartLocationCoordinate().equals(vehicle.getEndLocationCoordinate())
                        && vehicle.getStartLocationId().equals(vehicle.getEndLocationId()))){
                    jsonGenerator.writeObjectFieldStart(JsonConstants.Vehicle.END_ADDRESS);
                    jsonGenerator.writeStringField(JsonConstants.Address.ID, vehicle.getEndLocationId());
                    jsonGenerator.writeNumberField(JsonConstants.Address.LON, vehicle.getEndLocationCoordinate().getX());
                    jsonGenerator.writeNumberField(JsonConstants.Address.LAT,vehicle.getEndLocationCoordinate().getY());
                    jsonGenerator.writeEndObject();
                }
                jsonGenerator.writeNumberField(JsonConstants.Vehicle.EARLIEST_START,vehicle.getEarliestDeparture());
                jsonGenerator.writeNumberField(JsonConstants.Vehicle.LATEST_END,vehicle.getLatestArrival());

                jsonGenerator.writeStringField(JsonConstants.Vehicle.TYPE_ID,vehicle.getType().getTypeId());

                jsonGenerator.writeArrayFieldStart(JsonConstants.Vehicle.SKILLS);
                for(String skill : vehicle.getSkills().values()){
                    jsonGenerator.writeString(skill);
                }
                jsonGenerator.writeEndArray();
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void writeServices(JsonGenerator jsonGenerator) {
        try {
            jsonGenerator.writeArrayFieldStart(JsonConstants.SERVICES);
            for(Job job : vrp.getJobs().values()){
                if(!(job instanceof Service)) continue;
                Service service = (Service)job;
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField(JsonConstants.Job.ID, service.getId());
                jsonGenerator.writeStringField(JsonConstants.Job.TYPE,service.getType());
                jsonGenerator.writeStringField(JsonConstants.Job.NAME,service.getName());
                jsonGenerator.writeObjectFieldStart(JsonConstants.Job.ADDRESS);
                jsonGenerator.writeStringField(JsonConstants.Address.ID, service.getLocationId());
                jsonGenerator.writeNumberField(JsonConstants.Address.LON, service.getCoord().getX());
                jsonGenerator.writeNumberField(JsonConstants.Address.LAT,service.getCoord().getY());
                jsonGenerator.writeEndObject();
                jsonGenerator.writeNumberField(JsonConstants.Job.SERVICE_DURATION, service.getServiceDuration());

                jsonGenerator.writeObjectFieldStart(JsonConstants.Job.TIME_WINDOW);
                jsonGenerator.writeNumberField(JsonConstants.TimeWindow.START,service.getTimeWindow().getStart());
                jsonGenerator.writeNumberField(JsonConstants.TimeWindow.END,service.getTimeWindow().getEnd());
                jsonGenerator.writeEndObject();

                jsonGenerator.writeArrayFieldStart(JsonConstants.Job.SIZE);
                for(int i=0;i<service.getSize().getNuOfDimensions();i++){
                    jsonGenerator.writeNumber(service.getSize().get(i));
                }
                jsonGenerator.writeEndArray();

                jsonGenerator.writeArrayFieldStart(JsonConstants.Job.SKILLS);
                for(String skill : service.getRequiredSkills().values()){
                    jsonGenerator.writeString(skill);
                }
                jsonGenerator.writeEndArray();
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        Service service = Service.Builder.newInstance("s1").setLocationId("s1_loc").setCoord(Coordinate.newInstance(40, 10))
                .addSizeDimension(0, 20).addSizeDimension(1, 40)
                .setServiceTime(1.)
                .addRequiredSkill("joo-foo")
                .build();
        Pickup service2 = (Pickup) Pickup.Builder.newInstance("pickup2").setLocationId("s2_loc").setCoord(Coordinate.newInstance(40, 10))
                .addSizeDimension(0, 10).addSizeDimension(1, 30)
                .setServiceTime(2.)
                .setTimeWindow(TimeWindow.newInstance(10, 200))
                .addRequiredSkill("screw-driver").build();
        VehicleType type = VehicleTypeImpl.Builder.newInstance("small").addCapacityDimension(0,100).addCapacityDimension(1,400)
                .setCostPerTime(20.).build();

        VehicleType type2 = VehicleTypeImpl.Builder.newInstance("medium").addCapacityDimension(0,1000).addCapacityDimension(1,4000)
                .setCostPerTime(200.).setFixedCost(1000.).build();

        VehicleImpl v1 = VehicleImpl.Builder.newInstance("v1").setStartLocationId("startLoc").setStartLocationCoordinate(Coordinate.newInstance(0, 0))
                .setEndLocationId("endLoc").setEndLocationCoordinate(Coordinate.newInstance(12, 12))
                .addSkill("screw-driver")
                .setType(type)
                .setLatestArrival(1000.)
                .build();

        VehicleImpl v2 = VehicleImpl.Builder.newInstance("v2").setStartLocationId("startLoc").setStartLocationCoordinate(Coordinate.newInstance(0, 0))
                .setType(type2)
                .setReturnToDepot(false)
                .addSkill("joo")
                .build();

        final VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance().addJob(service).addJob(service2)
                .addVehicle(v1).addVehicle(v2).build();
        new VrpJsonWriter(vrp).write(new File("output/vrp.json"));
//        System.out.println(new VrpJsonWriter(vrp).toString());

        VehicleRoutingProblem.Builder vrpBuilder_ = VehicleRoutingProblem.Builder.newInstance();
        new VrpJsonReader(vrpBuilder_).read("output/vpr.json");
        VehicleRoutingProblem vrp_ = vrpBuilder_.build();

        VehicleRoutingAlgorithm vra = new GreedySchrimpfFactory().createAlgorithm(vrp_);
        VehicleRoutingProblemSolution solutions = Solutions.bestOf(vra.searchSolutions());

        new VrpJsonWriter(vrp,solutions,new SolutionAnalyser.DistanceCalculator() {

            @Override
            public double getDistance(String fromLocationId, String toLocationId) {
                return vrp.getTransportCosts().getTransportCost(fromLocationId,toLocationId,0.,null,null);
            }
        }).write(new File("output/vrp-solution.json"));

    }
}
