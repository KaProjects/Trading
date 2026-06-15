import 'dotenv/config';
import { InstallGlobalCommands } from './utils.js';

const TEST_COMMAND = {
  name: 'test',
  description: 'Basic command',
  type: 1,
  integration_types: [0],
  contexts: [0],
};

const EARNINGS_COMMAND = {
  name: 'earnings',
  description: 'Prints current earnings for the company',
  type: 1,
  integration_types: [0],
  contexts: [0],
};

InstallGlobalCommands(process.env.APP_ID, [TEST_COMMAND, EARNINGS_COMMAND]);
