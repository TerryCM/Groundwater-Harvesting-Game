package edu.asu.commons.foraging.bot;


import edu.asu.commons.foraging.model.GroupDataModel;

public class CustomBot extends Bot.SimpleBot {

    private static final long serialVersionUID = 4732276788271013068L;

    public final static int ACTIONS_PER_SECOND = 6;

    public final static double HARVEST_PROBABILITY = 0.6d;

    public final static double MOVEMENT_PROBABILITY = 0.9d;

    public CustomBot() {
        super(ACTIONS_PER_SECOND, MOVEMENT_PROBABILITY, HARVEST_PROBABILITY);
    }

    public CustomBot(int actionsPerSecond, double movementProbability, double harvestProbability) {
        super(actionsPerSecond, movementProbability, harvestProbability);
    }

    @Override
    public BotType getBotType() {
        return BotType.CUSTOM;
    }

}
