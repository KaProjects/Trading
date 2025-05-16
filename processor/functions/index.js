import {logger}  from "firebase-functions";
import {onRequest} from "firebase-functions/v2/https";
import {getDatabase} from "firebase-admin/database";
import {initializeApp} from "firebase-admin/app";
import {InteractionType, verifyKey} from "discord-interactions";

const app = initializeApp({"databaseURL": process.env.DATABASE});


export const interactions = onRequest(async (req, res) => {
    const token = req.query.token
    if (!token) {
        logger.warn("Token missing");
        return res.status(401).send("Unauthorized: No token provided");
    }
    if (token !== process.env.REQUEST_TOKEN) {
        logger.warn("Token verification failed: " + token);
        res.status(401).send("Unauthorized: Invalid token");
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
            return res.status(200).send(JSON.stringify({ type: 1 }));
        case InteractionType.APPLICATION_COMMAND:
            const { name } = data;
            switch (name) {
                case 'test':
                    const ticker = req.body.channel.name.toUpperCase()
                    return await getDatabase(app).ref("company/" + ticker).get().then(value => {
                            res.set("Content-Type", "application/json");
                            return res.status(200).send(JSON.stringify({
                                type: 4,
                                data: { content: JSON.stringify(value) },
                            }));
                    }).catch(error => {
                        logger.error(`database error: ${error}`)
                        return res.status(400).json({ error: 'database error' })
                    })
                default:
                    logger.error(`unknown command: ${name}`)
                    return res.status(400).json({ error: 'unknown command' })
            }
        default:
            logger.error(`unknown interaction type: ${type}`);
            return res.status(400).json({ error: 'unknown interaction type' });
    }
})