import {logger} from "firebase-functions";
import {InteractionResponseFlags, InteractionResponseType} from "discord-interactions";
import {simpleMessageResponseBody} from "./utils.js";


function createComponent(data) {
    data.reverse()
    const texts = []
    texts.push({"type": 10, "content": "### Earnings Reports"})

    for (const index in data) {
        const earnings = data[index]

        let revEst = Number(earnings.revenueEstimate) / 1000000;
        if (revEst > 1000) {
            revEst = String((revEst/1000).toFixed(2) + "B")
        } else {
            revEst = String((revEst).toFixed(2) + "M")
        }

        let report = ""
        if (earnings.epsActual && earnings.revenueActual) {
            const epsReported = String(Number(earnings.epsActual).toFixed(2)).padStart(5," ")
            let revReported = Number(earnings.revenueActual) / 1000000;
            if (revReported > 1000) {
                revReported = String((revReported/1000).toFixed(2) + "B")
            } else {
                revReported = String((revReported).toFixed(2) + "M")
            }
            report += "\nReported:  (eps=`" + epsReported + "`, revenues=`" + revReported.padStart(7, " ") + "`)"
        }

        texts.push({"type": 14, "divider": true, "spacing": 1})
        texts.push({
            "type": 10,
            "content": "[`" + earnings.date.toISOString().split("T")[0] + "`|`"
                        + String(earnings.year).substring(2,4) + "Q" + String(earnings.quarter) + "`]"
                        + "\nEstimates: (eps=`" + String(Number(earnings.epsEstimate).toFixed(2)).padStart(5," ") + "`"
                        + ", revenues=`" +  revEst.padStart(7, " ") + "`)"
                        + report
        })
    }

    return {
        "type": InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
        "data": {
            "flags": InteractionResponseFlags.IS_COMPONENTS_V2,
            "components": [
                {
                    "type": 17,
                    "accent_color": 0x008000,
                    "components": texts
                }
            ]
        }
    }
}

export function responseDiscordComponent(req, res, fhClient) {
    const ticker = req.body.channel.name.toUpperCase()

    const today = new Date();
    let mm = today.getMonth() + 1
    let yyyy = today.getFullYear();

    const toDate = String(yyyy + 1) + "-" + String(mm).padStart(2, '0') + "-" + String(today.getDate()).padStart(2, '0')

    mm = mm - 6
    if (mm < 1) {
        mm = mm + 12
        yyyy = yyyy - 1
    }

    const fromDate = String(yyyy) + "-" + String(mm).padStart(2, '0') + "-01"

    fhClient.earningsCalendar({"from": fromDate, "to": toDate, "symbol": ticker}, (error, data, response) => {
        res.set("Content-Type", "application/json");
        if (!error) {
            if (data.earningsCalendar.length === 0) {
                return res.status(200).send(simpleMessageResponseBody("Not Found"))
            }
            return res.status(200).send(JSON.stringify(createComponent(data.earningsCalendar)))
        } else {
            logger.error(`finnhub error: ${error}`)
            return res.status(400).json({ error: 'web service api error' })
        }
    });
}