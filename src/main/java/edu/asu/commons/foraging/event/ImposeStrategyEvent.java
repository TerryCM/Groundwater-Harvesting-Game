package edu.asu.commons.foraging.event;

import edu.asu.commons.event.AbstractPersistableEvent;
import edu.asu.commons.foraging.rules.Strategy;
import edu.asu.commons.net.Identifier;

public class ImposeStrategyEvent extends AbstractPersistableEvent {

	private static final long serialVersionUID = -7231412845435362871L;

	private final Strategy strategy;

	public ImposeStrategyEvent(Identifier id, Strategy strategy) {
		super(id, strategy.toString());
		this.strategy = strategy;
	}

	public Strategy getStrategy() {
		return strategy;
	}

}
