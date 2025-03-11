package me.micartey.jation.adapter.network.packets;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.micartey.jation.adapter.network.serializer.Serialize;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PacketInvokeMethod {

    @Serialize("ackId")
    private int ackId;

    @Serialize("eventData")
    public String eventData;

    @Serialize("additionalObjects")
    public String additionalObjects;

}
