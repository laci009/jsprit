/*******************************************************************************
 * Copyright (C) 2013  Stefan Schroeder
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
package jsprit.core.algorithm;

import jsprit.core.algorithm.acceptor.SolutionAcceptor;
import jsprit.core.algorithm.listener.SearchStrategyModuleListener;
import jsprit.core.algorithm.selector.SolutionSelector;
import jsprit.core.problem.VehicleRoutingProblem;
import jsprit.core.problem.solution.SolutionCostCalculator;
import jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;


public class SearchStrategy {
	
	public static class DiscoveredSolution {
		private VehicleRoutingProblemSolution solution;
		private boolean accepted;
		private String strategyName;
		
		public DiscoveredSolution(VehicleRoutingProblemSolution solution,boolean accepted, String strategyName) {
			super();
			this.solution = solution;
			this.accepted = accepted;
			this.strategyName = strategyName;
		}

		public VehicleRoutingProblemSolution getSolution() {
			return solution;
		}

		public boolean isAccepted() {
			return accepted;
		}

		@SuppressWarnings("UnusedDeclaration")
        public String getStrategyName() {
			return strategyName;
		}
		
	}
	
	private static Logger logger = LogManager.getLogger(SearchStrategy.class);
	
	private Collection<SearchStrategyModule> searchStrategyModules = new ArrayList<SearchStrategyModule>();
	
	private SolutionSelector solutionSelector;
	
	private SolutionCostCalculator solutionCostCalculator;

	private SolutionAcceptor solutionAcceptor;
	
	private String name;
	
	public SearchStrategy(SolutionSelector solutionSelector, SolutionAcceptor solutionAcceptor, SolutionCostCalculator solutionCostCalculator) {
		super();
		this.solutionSelector = solutionSelector;
		this.solutionAcceptor = solutionAcceptor;
		this.solutionCostCalculator = solutionCostCalculator;
		logger.info("initialise " + this);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Collection<SearchStrategyModule> getSearchStrategyModules() {
		return Collections.unmodifiableCollection(searchStrategyModules);
	}

	@SuppressWarnings("UnusedDeclaration")
    public SolutionSelector getSolutionSelector() {
		return solutionSelector;
	}

    @SuppressWarnings("UnusedDeclaration")
	public SolutionAcceptor getSolutionAcceptor() {
		return solutionAcceptor;
	}

	@Override
	public String toString() {
		return "searchStrategy [#modules="+searchStrategyModules.size()+"][selector="+solutionSelector+"][acceptor="+solutionAcceptor+"]";
	}
 
	/**
	 * Runs the search-strategy and its according modules, and returns DiscoveredSolution.
	 * 
	 * <p>This involves three basic steps: 1) Selecting a solution from solutions (input parameter) according to {@link jsprit.core.algorithm.selector.SolutionSelector}, 2) running the modules
	 * ({@link jsprit.core.algorithm.SearchStrategyModule}) on the selectedSolution and 3) accepting the new solution according to {@link jsprit.core.algorithm.acceptor.SolutionAcceptor}.
	 * <p> Note that after 1) the selected solution is copied, thus the original solution is not modified.
	 * <p> Note also that 3) modifies the input parameter solutions by adding, removing, replacing the existing solutions or whatever is defined in the solutionAcceptor.
	 *  
	 * @param vrp the underlying vehicle routing problem
	 * @param solutions which will be modified 
	 * @return discoveredSolution
     * @throws java.lang.IllegalStateException if selector cannot select any solution
     */
	@SuppressWarnings("UnusedParameters")
    public DiscoveredSolution run(VehicleRoutingProblem vrp, Collection<VehicleRoutingProblemSolution> solutions){
		VehicleRoutingProblemSolution solution = solutionSelector.selectSolution(solutions);
		if(solution == null) throw new IllegalStateException(getErrMsg());
		VehicleRoutingProblemSolution lastSolution = VehicleRoutingProblemSolution.copyOf(solution);
		for(SearchStrategyModule module : searchStrategyModules){
            lastSolution = module.runAndGetSolution(lastSolution);
		}
		double costs = solutionCostCalculator.getCosts(lastSolution);
		lastSolution.setCost(costs);
		boolean solutionAccepted = solutionAcceptor.acceptSolution(solutions, lastSolution);
		return new DiscoveredSolution(lastSolution, solutionAccepted, getName());
	}

    private String getErrMsg() {
        return "solution is null. check solutionSelector to return an appropriate solution. " +
                "\nfigure out whether you start with an initial solution. either you set it manually by algorithm.addInitialSolution(...)"
               + " or let the algorithm create an initial solution for you. then add the <construction>...</construction> xml-snippet to your algorithm's config file.";
    }


    public void addModule(SearchStrategyModule module){
		if(module == null) throw new IllegalStateException("module to be added is null.");
		searchStrategyModules.add(module);
		logger.info("module added [module="+module+"][#modules="+searchStrategyModules.size()+"]");
	}

	public void addModuleListener(SearchStrategyModuleListener moduleListener) {
		for(SearchStrategyModule module : searchStrategyModules){
			module.addModuleListener(moduleListener);
		}
		
	}

}
