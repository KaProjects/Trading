import {InteractionResponseType} from "discord-interactions";

export async function DiscordRequest(endpoint, options) {
  const url = 'https://discord.com/api/v10/' + endpoint;
  if (options.body) options.body = JSON.stringify(options.body);
  const res = await fetch(url, {
    headers: {
      Authorization: `Bot ${process.env.DISCORD_TOKEN}`,
      'Content-Type': 'application/json; charset=UTF-8',
      'User-Agent': 'DiscordBot (https://github.com/discord/discord-example-app, 1.0.0)',
    },
    ...options
  });
  if (!res.ok) {
    const data = await res.json();
    console.log(res.status);
    throw new Error(JSON.stringify(data));
  }
  return res;
}

export async function InstallGlobalCommands(appId, commands) {
  const endpoint = `applications/${appId}/commands`;
  try {
    await DiscordRequest(endpoint, { method: 'PUT', body: commands });
  } catch (err) {
    console.error(err);
  }
}

export async function DeleteMessage(application_id, token) {
  const endpoint = `webhooks/${application_id}/${token}/messages/@original`;
  try {
    await DiscordRequest(endpoint, { method: 'DELETE'});
  } catch (err) {
    console.error(err);
  }
}

export function simpleMessageResponseBody(message) {
  return JSON.stringify({
    type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
    data: { content: message },
  })
}