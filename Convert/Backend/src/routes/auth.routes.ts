import { Router } from 'express';
import { registerDevice, signIn, getProfile } from '../controllers/auth.controller';
import { verifyAppSignature } from '../middlewares/signature.middleware';

const router = Router();

router.post('/device/register', verifyAppSignature, registerDevice);
router.post('/signin', verifyAppSignature, signIn);
router.get('/user/profile', getProfile);

export default router;
