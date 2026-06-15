import {logger}  from "firebase-functions";
import {onRequest} from "firebase-functions/v2/https";
import {getDatabase} from "firebase-admin/database";
import {initializeApp} from "firebase-admin/app";
import {InteractionResponseType, InteractionType, verifyKey} from "discord-interactions";
import finnhub from "finnhub";
import {responseDiscordComponent} from "./earnings.js";
import {simpleMessageResponseBody} from "./utils.js";

const app = initializeApp({"databaseURL": process.env.DATABASE});

const api_key = finnhub.ApiClient.instance.authentications['api_key'];
api_key.apiKey = process.env.FINNHUB_API_KEY
const fhClient = new finnhub.DefaultApi()


async function test(req, res) {
    const ticker = req.body.channel.name.toUpperCase()
    return await getDatabase(app).ref("company/" + ticker).get().then(value => {
        res.set("Content-Type", "application/json");
        return res.status(200).send(simpleMessageResponseBody(JSON.stringify(value)))
    }).catch(error => {
        logger.error(`database error: ${error}`)
        return res.status(400).json({ error: 'database error' })
    })
}

export const interactions = onRequest(async (req, res) => {
    const token = req.query.token
    if (!token) {
        logger.warn("Token missing");
        return res.status(401).send("Unauthorized");
    }
    if (token !== process.env.REQUEST_TOKEN) {
        logger.warn("Token verification failed: " + token);
        return res.status(401).send("Unauthorized");
    }

    if (!await verifyKey(
        new TextEncoder().encode(JSON.stringify(req.body)),
        req.header("x-signature-ed25519"),
        req.header("x-signature-timestamp"),
        process.env.PUBLIC_KEY
    )){
        return res.status(400).json({ error: 'invalid request' })
    }

    const { id, type, data } = req.body;

    switch (type) {
        case InteractionType.PING:
            res.set("Content-Type", "application/json");
            return res.status(200).send(JSON.stringify({ type: InteractionResponseType.PONG }));
        case InteractionType.APPLICATION_COMMAND:
            const { name } = data;
            switch (name) {
                case 'test':
                    return await test(req, res);
                case 'earnings':
                    return responseDiscordComponent(req, res, fhClient);
                default:
                    logger.error(`unknown command: ${name}`)
                    return res.status(400).json({ error: 'unknown command' })
            }
        default:
            logger.error(`unknown interaction type: ${type}`);
            return res.status(400).json({ error: 'unknown interaction type' });
    }
})