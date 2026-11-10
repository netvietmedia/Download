import { Router } from 'express';
import multer from 'multer';
import { createRelaySession, joinRelaySession, uploadRelayChunk, downloadRelayChunk } from '../controllers/relay.controller';
import { config } from '../config';

const upload = multer({ dest: config.relayTempDir });
const router = Router();

router.post('/session/create', createRelaySession);
router.post('/session/join', joinRelaySession);
router.post('/chunk/upload', upload.single('chunk'), uploadRelayChunk);
router.get('/chunk/download/:session_id/:chunk_index', downloadRelayChunk);

export default router;

