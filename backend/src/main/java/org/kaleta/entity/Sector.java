package org.kaleta.entity;

import lombok.Getter;

public enum Sector
{
    SEMICONDUCTORS("Semiconductors"),
    SOFTWARE("Technology - Software"),
    HARDWARE("Technology - Hardware"),
    CONSUMER_ELECTRONICS("Consumer Electronics"),
    COMMUNICATION_SERVICES("Communication Services"),
    ELECTRIC_VEHICLES("Electric Vehicles"),
    MOTOR_VEHICLES("Motor Vehicles"),
    INTERNET_RETAIL("Internet Retail "),
    ENERGY_MINERALS("Energy Minerals"),
    FINANCE("Finance"),
    MARINE_SHIPPING("Marine Shipping"),
    NON_DURABLES("Consumer Non-Durables");

    @Getter
    private final String name;

    Sector(String name)
    {
        this.name = name;
    }
}
